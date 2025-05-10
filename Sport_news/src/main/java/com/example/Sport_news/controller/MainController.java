package com.example.Sport_news.controller;

import com.example.Sport_news.model.Contact;
import com.example.Sport_news.model.News;
import com.example.Sport_news.model.About;
import com.example.Sport_news.repository.ContactRepository;
import com.example.Sport_news.repository.NewsRepository;
import com.example.Sport_news.repository.AboutRepository;
import com.example.Sport_news.service.FileStorageService;
import com.example.Sport_news.service.VkNewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AboutRepository aboutRepository;

    @Autowired
    private VkNewsService vkNewsService;

    @Autowired
    private FileStorageService fileStorageService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("news", newsRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("contacts", contactRepository.findAll());
        model.addAttribute("about", aboutRepository.findById(1L).orElse(new About()));
        return "index";
    }

    // News endpoints
    @GetMapping("/news/{id}")
    @ResponseBody
    public News getNews(@PathVariable Long id) {
        return newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("News not found"));
    }

    @PostMapping("/news/add")
    @ResponseBody
    public News addNews(@RequestParam("title") String title,
                       @RequestParam("content") String content,
                       @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile) {
        News news = new News();
        news.setTitle(title);
        news.setContent(content);
        news.setSourceType(News.NewsSource.MANUAL);

        if (mediaFile != null && !mediaFile.isEmpty()) {
            try {
                String filename = fileStorageService.storeFile(mediaFile);
                news.setMediaFileName(filename);
                news.setMediaFileType(mediaFile.getContentType());
                news.setMediaFilePath("/files/" + filename);
            } catch (IOException e) {
                throw new RuntimeException("Failed to store file", e);
            }
        }

        return newsRepository.save(news);
    }

    @PutMapping("/news/{id}")
    @ResponseBody
    public News editNews(@PathVariable Long id,
                        @RequestParam("title") String title,
                        @RequestParam("content") String content,
                        @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile) {
        News existingNews = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("News not found"));
        
        existingNews.setTitle(title);
        existingNews.setContent(content);

        if (mediaFile != null && !mediaFile.isEmpty()) {
            try {
                // Delete old media files if they exist
                if (existingNews.getMediaFileName() != null) {
                    String[] oldFiles = existingNews.getMediaFileName().split(",");
                    for (String oldFile : oldFiles) {
                        if (oldFile != null && !oldFile.isEmpty()) {
                            fileStorageService.deleteFile(oldFile.trim());
                        }
                    }
                }
                
                String filename = fileStorageService.storeFile(mediaFile);
                existingNews.setMediaFileName(filename);
                existingNews.setMediaFileType(mediaFile.getContentType());
                existingNews.setMediaFilePath("/files/" + filename);
            } catch (IOException e) {
                throw new RuntimeException("Failed to store file", e);
            }
        }

        return newsRepository.save(existingNews);
    }

    @DeleteMapping("/news/{id}")
    @ResponseBody
    public String deleteNews(@PathVariable Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("News not found"));
        
        if (news.getMediaFileName() != null) {
            try {
                fileStorageService.deleteFile(news.getMediaFileName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        newsRepository.deleteById(id);
        return "News deleted successfully";
    }

    // Contact endpoints
    @GetMapping("/contacts/{id}")
    @ResponseBody
    public Contact getContact(@PathVariable Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
    }

    @PostMapping("/contacts/add")
    @ResponseBody
    public Contact addContact(@RequestBody Contact contact) {
        return contactRepository.save(contact);
    }

    @PutMapping("/contacts/{id}")
    @ResponseBody
    public Contact editContact(@PathVariable Long id, @RequestBody Contact contact) {
        Contact existingContact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
        existingContact.setTitle(contact.getTitle());
        existingContact.setContent(contact.getContent());
        existingContact.setType(contact.getType());
        return contactRepository.save(existingContact);
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseBody
    public String deleteContact(@PathVariable Long id) {
        contactRepository.deleteById(id);
        return "Contact deleted successfully";
    }

    // About endpoints
    @GetMapping("/about")
    @ResponseBody
    public About getAbout() {
        return aboutRepository.findById(1L).orElse(new About());
    }

    @PutMapping("/about")
    @ResponseBody
    public About updateAbout(@RequestBody About about) {
        About existingAbout = aboutRepository.findById(1L).orElse(new About());
        existingAbout.setContent(about.getContent());
        return aboutRepository.save(existingAbout);
    }

    // File serving endpoint
    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadDir).resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/octet-stream"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/news/refresh")
    @ResponseBody
    @Transactional
    public Map<String, Object> refreshNews() {
        Map<String, Object> response = new HashMap<>();
        try {
            logger.info("Starting news refresh from VK");
            
            // Delete all existing VK news
            int deletedCount = newsRepository.deleteBySourceType(News.NewsSource.VK);
            logger.info("Deleted {} existing VK news items", deletedCount);
            response.put("deletedCount", deletedCount);
            
            // Fetch and save new VK news
            List<News> newNews = vkNewsService.fetchNews();
            logger.info("Fetched {} new news items from VK", newNews.size());
            response.put("totalCount", newNews.size());
            
            int savedCount = 0;
            for (News news : newNews) {
                try {
                    // Ensure the date is preserved
                    LocalDateTime originalDate = news.getCreatedAt();
                    News savedNews = newsRepository.save(news);
                    
                    // Verify the date was saved correctly
                    if (!savedNews.getCreatedAt().equals(originalDate)) {
                        logger.warn("Date mismatch detected for news {}: original={}, saved={}", 
                            savedNews.getTitle(), originalDate, savedNews.getCreatedAt());
                        // Force update the date
                        savedNews.setCreatedAt(originalDate);
                        savedNews = newsRepository.save(savedNews);
                    }
                    
                    savedCount++;
                    response.put("currentCount", savedCount);
                } catch (Exception e) {
                    logger.error("Error saving news item: {}", e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", String.format("News refreshed successfully. Deleted %d old items, added %d new items.", 
                               deletedCount, savedCount));
            return response;
        } catch (Exception e) {
            logger.error("Error refreshing news: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error refreshing news: " + e.getMessage());
            return response;
        }
    }
} 