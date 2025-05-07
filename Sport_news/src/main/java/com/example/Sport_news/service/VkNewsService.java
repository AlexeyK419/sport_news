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
import java.util.ArrayList;
import java.util.List;
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
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String fileExtension = getFileExtension(fileType);
            String newFilename = UUID.randomUUID().toString() + fileExtension;

            // Clean and encode URL
            String cleanUrl = cleanAndEncodeUrl(url);
            logger.info("Downloading media from URL: {}", cleanUrl);

            // Download and save file
            Path targetLocation = uploadPath.resolve(newFilename);
            try (var in = new URL(cleanUrl).openStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            
            return newFilename;
        } catch (Exception e) {
            logger.error("Error downloading media file from URL: " + url, e);
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
                    .count(10)
                    .execute()
                    .toString();

            logger.info("Raw VK API response: {}", response);
            
            // Parse the response string into a proper JSON object
            String cleanResponse = response.replace("(", "{").replace(")", "}")
                    .replace("'", "\"")
                    .replace("::", ":")
                    .replace("O", "0");
            
            JSONObject jsonResponse = new JSONObject(cleanResponse);
            
            // Check if there's an error in the response
            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMsg = String.format("VK API Error: %s (code: %d)", 
                    error.getString("error_msg"), 
                    error.getInt("error_code"));
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            JSONArray items = jsonResponse.getJSONArray("items");
            List<News> newsList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject post = items.getJSONObject(i);
                News news = new News();
                
                // Get text content, handling both text and description fields
                String text = "";
                if (post.has("text") && !post.getString("text").isEmpty()) {
                    text = post.getString("text");
                } else if (post.has("description")) {
                    text = post.getString("description");
                }
                
                // Set title as first 100 characters of text
                news.setTitle(text.substring(0, Math.min(100, text.length())));
                news.setContent(text);
                news.setSourceType(News.NewsSource.VK);
                news.setSourceId(String.valueOf(post.getInt("id")));

                // Handle attachments (photos, videos, etc.)
                if (post.has("attachments")) {
                    JSONArray attachments = post.getJSONArray("attachments");
                    for (int j = 0; j < attachments.length(); j++) {
                        JSONObject attachment = attachments.getJSONObject(j);
                        String type = attachment.getString("type");

                        if (type.equals("photo")) {
                            JSONObject photo = attachment.getJSONObject("photo");
                            // Get the largest available photo size
                            JSONArray sizes = photo.getJSONArray("sizes");
                            String photoUrl = "";
                            int maxSize = 0;
                            for (int k = 0; k < sizes.length(); k++) {
                                JSONObject size = sizes.getJSONObject(k);
                                int width = size.getInt("width");
                                if (width > maxSize) {
                                    maxSize = width;
                                    photoUrl = size.getString("url");
                                }
                            }
                            if (!photoUrl.isEmpty()) {
                                try {
                                    String filename = downloadAndSaveMedia(photoUrl, "image/jpeg");
                                    news.setMediaFileName(filename);
                                    news.setMediaFileType("image/jpeg");
                                    news.setMediaFilePath("/files/" + filename);
                                } catch (IOException e) {
                                    logger.error("Failed to download photo: " + photoUrl, e);
                                }
                            }
                        } else if (type.equals("video")) {
                            JSONObject video = attachment.getJSONObject("video");
                            if (video.has("player")) {
                                String videoUrl = video.getString("player");
                                try {
                                    String filename = downloadAndSaveMedia(videoUrl, "video/mp4");
                                    news.setMediaFileName(filename);
                                    news.setMediaFileType("video/mp4");
                                    news.setMediaFilePath("/files/" + filename);
                                } catch (IOException e) {
                                    logger.error("Failed to download video: " + videoUrl, e);
                                }
                            }
                        }
                    }
                }

                newsList.add(news);
            }

            logger.info("Successfully fetched {} news items from VK", newsList.size());
            return newsList;
        } catch (Exception e) {
            logger.error("Error fetching news from VK: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch news from VK: " + e.getMessage(), e);
        }
    }
} 