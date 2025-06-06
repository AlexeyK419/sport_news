package com.example.Sport_news.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "about")
public class About {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;
} 