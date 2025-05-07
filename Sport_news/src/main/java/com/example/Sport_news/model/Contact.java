package com.example.Sport_news.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "contacts")
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "contact_type")
    @Enumerated(EnumType.STRING)
    private ContactType type;

    public enum ContactType {
        PHONE,
        EMAIL,
        ADDRESS,
        SOCIAL_MEDIA,
        OTHER
    }
} 