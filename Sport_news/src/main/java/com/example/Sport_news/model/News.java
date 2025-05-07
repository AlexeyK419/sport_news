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

    @Column(name = "media_file_name")
    private String mediaFileName;

    @Column(name = "media_file_type")
    private String mediaFileType;

    @Column(name = "media_file_path")
    private String mediaFilePath;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum NewsSource {
        VK,
        MANUAL
    }
} 