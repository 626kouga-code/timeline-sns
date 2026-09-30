package com.timelinesns.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private String email;

    // Google ログインだけのユーザーは null
    private String passwordHash;

    private Instant emailVerifiedAt;

    @Column(nullable = false)
    private String handle;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String bio = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String handle, String displayName, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.handle = handle;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    /**
     * メールアドレスの確認（F-02）が済んだことを記録する。
     */
    public void markEmailVerified(Instant verifiedAt) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = verifiedAt;
        }
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getHandle() {
        return handle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
