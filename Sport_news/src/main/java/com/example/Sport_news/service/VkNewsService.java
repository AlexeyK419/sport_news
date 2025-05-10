package com.example.Sport_news.service;

import com.example.Sport_news.model.News;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.ServiceActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class VkNewsService {
    private static final Logger logger = LoggerFactory.getLogger(VkNewsService.class);
    private final VkApiClient vk;
    private final ServiceActor actor;
    private final String groupId;
    private final RestTemplate restTemplate;
    private final String uploadDir;

    public VkNewsService(
            @Value("${vk.app.id}") Integer appId,
            @Value("${vk.app.secret}") String appSecret,
            @Value("${vk.group.id}") String groupId,
            @Value("${file.upload-dir}") String uploadDir) {
        this.vk = new VkApiClient(new HttpTransportClient());
        this.actor = new ServiceActor(appId, appSecret);
        this.groupId = groupId;
        this.restTemplate = new RestTemplate();
        this.uploadDir = uploadDir;
        logger.info("VkNewsService initialized with appId: {}, groupId: {}", appId, groupId);
    }

    private String downloadAndSaveMedia(String url, String fileType) throws IOException {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileExtension = getFileExtension(fileType);
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path targetLocation = uploadPath.resolve(newFilename);

            try (var in = new URL(url).openStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            
            return newFilename;
        } catch (Exception e) {
            throw new IOException("Failed to download media file: " + e.getMessage(), e);
        }
    }

    private String cleanAndEncodeUrl(String url) {
        try {
            // Remove any whitespace
            url = url.trim();
            
            // Split URL and parameters
            String[] parts = url.split("\\?");
            String baseUrl = parts[0];
            String params = parts.length > 1 ? parts[1] : "";
            
            // Clean up the base URL
            baseUrl = baseUrl.replaceAll("\\s+", "");
            
            // If there are parameters, encode them properly
            if (!params.isEmpty()) {
                String[] paramPairs = params.split("&");
                StringBuilder encodedParams = new StringBuilder();
                for (String pair : paramPairs) {
                    if (encodedParams.length() > 0) {
                        encodedParams.append("&");
                    }
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        encodedParams.append(URLEncoder.encode(keyValue[0], StandardCharsets.UTF_8))
                                   .append("=")
                                   .append(URLEncoder.encode(keyValue[1], StandardCharsets.UTF_8));
                    }
                }
                return baseUrl + "?" + encodedParams.toString();
            }
            
            return baseUrl;
        } catch (Exception e) {
            logger.error("Error cleaning URL: " + url, e);
            return url;
        }
    }

    private String getFileExtension(String fileType) {
        if (fileType.startsWith("image/")) {
            if (fileType.contains("jpeg") || fileType.contains("jpg")) return ".jpg";
            if (fileType.contains("png")) return ".png";
            if (fileType.contains("gif")) return ".gif";
            return ".jpg"; // default for images
        } else if (fileType.startsWith("video/")) {
            return ".mp4"; // default for videos
        }
        return ".bin"; // default for other types
    }

    public List<News> fetchNews() {
        try {
            logger.info("Attempting to fetch news from VK group: {}", groupId);
            String response = vk.wall().get(actor)
                    .ownerId(-Integer.parseInt(groupId))
                    .count(100)  // Увеличиваем количество новостей
                    .execute()
                    .toString();

            JSONObject jsonResponse = new JSONObject(response);
            
            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                throw new RuntimeException(String.format("VK API Error: %s (code: %d)", 
                    error.getString("error_msg"), 
                    error.getInt("error_code")));
            }

            JSONArray items = jsonResponse.getJSONArray("items");
            List<News> newsList = new ArrayList<>();
            Set<String> processedIds = new HashSet<>();

            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject post = items.getJSONObject(i);
                    String postId = String.valueOf(post.getInt("id"));
                    
                    if (processedIds.contains(postId)) {
                        continue;
                    }
                    processedIds.add(postId);
                    
                    News news = new News();
                    
                    String text = post.has("text") && !post.getString("text").isEmpty() 
                        ? post.getString("text") 
                        : post.has("description") ? post.getString("description") : "";
                    
                    if (text.isEmpty()) {
                        continue;
                    }
                    
                    news.setTitle(text.substring(0, Math.min(100, text.length())));
                    news.setContent(text);
                    news.setSourceType(News.NewsSource.VK);
                    news.setSourceId(postId);
                    
                    // Set the creation date from VK post
                    if (post.has("date")) {
                        long postDate = post.getLong("date");
                        LocalDateTime postDateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(postDate),
                            ZoneId.of("Europe/Moscow")
                        );
                        news.setCreatedAt(postDateTime);
                        logger.info("Post {} date set to: {}", postId, postDateTime);
                    } else {
                        news.setCreatedAt(LocalDateTime.now());
                    }

                    // Handle all attachments
                    if (post.has("attachments")) {
                        JSONArray attachments = post.getJSONArray("attachments");
                        StringBuilder mediaFiles = new StringBuilder();
                        StringBuilder mediaTypes = new StringBuilder();
                        StringBuilder mediaPaths = new StringBuilder();
                        
                        for (int j = 0; j < attachments.length(); j++) {
                            JSONObject attachment = attachments.getJSONObject(j);
                            String type = attachment.getString("type");
                            String mediaUrl = "";
                            String mediaType = "";

                            switch (type) {
                                case "photo":
                                    JSONObject photo = attachment.getJSONObject("photo");
                                    JSONArray sizes = photo.getJSONArray("sizes");
                                    int maxSize = 0;
                                    
                                    for (int k = 0; k < sizes.length(); k++) {
                                        JSONObject size = sizes.getJSONObject(k);
                                        int width = size.getInt("width");
                                        if (width > maxSize) {
                                            maxSize = width;
                                            mediaUrl = size.getString("url");
                                        }
                                    }
                                    mediaType = "image/jpeg";
                                    break;

                                case "video":
                                    JSONObject video = attachment.getJSONObject("video");
                                    if (video.has("player")) {
                                        mediaUrl = video.getString("player");
                                        mediaType = "video/mp4";
                                    } else if (video.has("files")) {
                                        JSONObject files = video.getJSONObject("files");
                                        if (files.has("mp4_720")) {
                                            mediaUrl = files.getString("mp4_720");
                                        } else if (files.has("mp4_480")) {
                                            mediaUrl = files.getString("mp4_480");
                                        } else if (files.has("mp4_360")) {
                                            mediaUrl = files.getString("mp4_360");
                                        } else if (files.has("mp4_240")) {
                                            mediaUrl = files.getString("mp4_240");
                                        }
                                        mediaType = "video/mp4";
                                    }
                                    break;

                                case "doc":
                                    JSONObject doc = attachment.getJSONObject("doc");
                                    mediaUrl = doc.getString("url");
                                    mediaType = doc.getString("type");
                                    break;

                                case "audio":
                                    JSONObject audio = attachment.getJSONObject("audio");
                                    mediaUrl = audio.getString("url");
                                    mediaType = "audio/mpeg";
                                    break;

                                case "link":
                                    JSONObject link = attachment.getJSONObject("link");
                                    if (link.has("photo")) {
                                        JSONObject linkPhoto = link.getJSONObject("photo");
                                        JSONArray linkSizes = linkPhoto.getJSONArray("sizes");
                                        int maxLinkSize = 0;
                                        
                                        for (int k = 0; k < linkSizes.length(); k++) {
                                            JSONObject size = linkSizes.getJSONObject(k);
                                            int width = size.getInt("width");
                                            if (width > maxLinkSize) {
                                                maxLinkSize = width;
                                                mediaUrl = size.getString("url");
                                            }
                                        }
                                        mediaType = "image/jpeg";
                                    }
                                    break;
                            }

                            if (!mediaUrl.isEmpty()) {
                                try {
                                    String filename = downloadAndSaveMedia(mediaUrl, mediaType);
                                    if (mediaFiles.length() > 0) {
                                        mediaFiles.append(",");
                                        mediaTypes.append(",");
                                        mediaPaths.append(",");
                                    }
                                    mediaFiles.append(filename);
                                    mediaTypes.append(mediaType);
                                    mediaPaths.append("/files/" + filename);
                                    logger.info("Successfully downloaded media for post {}: {}", postId, filename);
                                } catch (IOException e) {
                                    logger.error("Failed to download media for post {}: {}", postId, e.getMessage());
                                }
                            }
                        }

                        if (mediaFiles.length() > 0) {
                            news.setMediaFileName(mediaFiles.toString());
                            news.setMediaFileType(mediaTypes.toString());
                            news.setMediaFilePath(mediaPaths.toString());
                        }
                    }

                    newsList.add(news);
                    logger.info("Successfully processed post {} with {} attachments", postId, 
                        post.has("attachments") ? post.getJSONArray("attachments").length() : 0);
                } catch (Exception e) {
                    logger.error("Error processing post: {}", e.getMessage());
                }
            }

            logger.info("Successfully fetched {} news items from VK", newsList.size());
            return newsList;
        } catch (Exception e) {
            logger.error("Error fetching news from VK: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch news from VK: " + e.getMessage());
        }
    }
} 