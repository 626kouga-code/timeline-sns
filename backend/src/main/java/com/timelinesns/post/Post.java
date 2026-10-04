package com.timelinesns.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 投稿。id は時系列順に並ぶ UUIDv7 で、タイムラインの並び順とカーソルにそのまま使う。
 * 一覧・詳細の表示は {@link PostQuery} で取得する（件数や作者をまとめて 1 クエリで取るため）。
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected Post() {
    }

    public Post(UUID userId, String body, Instant createdAt) {
        this.userId = userId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public boolean isWrittenBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
