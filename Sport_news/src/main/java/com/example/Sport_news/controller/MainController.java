package com.example.Sport_news.controller;

import com.example.Sport_news.model.Contact;
import com.example.Sport_news.model.News;
import com.example.Sport_news.repository.ContactRepository;
import com.example.Sport_news.repository.NewsRepository;
import com.example.Sport_news.service.FileStorageService;
import com.example.Sport_news.service.VkNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class MainController {

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private ContactRepository contactRepository;

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
        return "index";
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

    @PutMapping("/news/edit/{id}")
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
                // Delete old file if exists
                if (existingNews.getMediaFileName() != null) {
                    fileStorageService.deleteFile(existingNews.getMediaFileName());
                }
                
                // Store new file
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

    @DeleteMapping("/news/delete/{id}")
    @ResponseBody
    public String deleteNews(@PathVariable Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("News not found"));
        
        // Delete associated file if exists
        if (news.getMediaFileName() != null) {
            try {
                fileStorageService.deleteFile(news.getMediaFileName());
            } catch (IOException e) {
                // Log error but continue with news deletion
                e.printStackTrace();
            }
        }
        
        newsRepository.deleteById(id);
        return "News deleted successfully";
    }

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

    @PostMapping("/contacts/add")
    @ResponseBody
    public Contact addContact(@RequestBody Contact contact) {
        return contactRepository.save(contact);
    }

    @PutMapping("/contacts/edit/{id}")
    @ResponseBody
    public Contact editContact(@PathVariable Long id, @RequestBody Contact contact) {
        Contact existingContact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
        existingContact.setTitle(contact.getTitle());
        existingContact.setContent(contact.getContent());
        existingContact.setType(contact.getType());
        return contactRepository.save(existingContact);
    }

    @DeleteMapping("/contacts/delete/{id}")
    @ResponseBody
    public String deleteContact(@PathVariable Long id) {
        contactRepository.deleteById(id);
        return "Contact deleted successfully";
    }

    @GetMapping("/news/refresh")
    @ResponseBody
    public String refreshNews() {
        try {
            vkNewsService.fetchNews().forEach(newsRepository::save);
            return "News refreshed successfully";
        } catch (Exception e) {
            return "Error refreshing news: " + e.getMessage();
        }
    }
} 