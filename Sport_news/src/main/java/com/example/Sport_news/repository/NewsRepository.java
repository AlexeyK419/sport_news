package com.example.Sport_news.repository;

import com.example.Sport_news.model.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    @Modifying
    @Query("DELETE FROM News n WHERE n.sourceType = ?1")
    int deleteBySourceType(News.NewsSource sourceType);
} 