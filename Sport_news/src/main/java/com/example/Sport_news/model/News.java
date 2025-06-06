package com.example.Sport_news.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "news")
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "source_type")
    @Enumerated(EnumType.STRING)
    private NewsSource sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "media_file_name", columnDefinition = "TEXT")
    private String mediaFileName;

    @Column(name = "media_file_type", columnDefinition = "TEXT")
    private String mediaFileType;

    @Column(name = "media_file_path", columnDefinition = "TEXT")
    private String mediaFilePath;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum NewsSource {
        VK,
        MANUAL
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public NewsSource getSourceType() {
        return sourceType;
    }

    public void setSourceType(NewsSource sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getMediaFileName() {
        return mediaFileName;
    }

    public void setMediaFileName(String mediaFileName) {
        this.mediaFileName = mediaFileName;
    }

    public String getMediaFileType() {
        return mediaFileType;
    }

    public void setMediaFileType(String mediaFileType) {
        this.mediaFileType = mediaFileType;
    }

    public String getMediaFilePath() {
        return mediaFilePath;
    }

    public void setMediaFilePath(String mediaFilePath) {
        this.mediaFilePath = mediaFilePath;
    }

    // Helper methods for handling multiple media files
    public String[] getMediaFileNames() {
        return mediaFileName != null ? mediaFileName.split(",") : new String[0];
    }

    public String[] getMediaFileTypes() {
        return mediaFileType != null ? mediaFileType.split(",") : new String[0];
    }

    public String[] getMediaFilePaths() {
        return mediaFilePath != null ? mediaFilePath.split(",") : new String[0];
    }
} 