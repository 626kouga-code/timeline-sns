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

    public static final int MAX_DISPLAY_NAME = 50;
    public static final int MAX_BIO = 160;

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

    // アイコン画像の保存先のキー（ObjectStorage）。未設定なら null
    private String avatarKey;

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

    /**
     * 表示名と自己紹介を変更する（F-41）。前後の空白は取り除く。
     */
    public void updateProfile(String displayName, String bio) {
        this.displayName = displayName.strip();
        this.bio = bio.strip();
    }

    /**
     * アイコン画像を差し替える（null なら削除）。戻り値は差し替える前のキーで、保存先から消すのに使う。
     */
    public String changeAvatar(String newKey) {
        String old = avatarKey;
        avatarKey = newKey;
        return old;
    }

    public String getAvatarKey() {
        return avatarKey;
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
