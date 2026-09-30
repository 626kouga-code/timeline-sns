-- 初期スキーマ（docs/05_data-model.md）
-- id は UUIDv7。通常はアプリ側で生成するが、手動投入用に DB の既定値も uuidv7()（PostgreSQL 18 の組み込み関数）にしておく。
-- 時刻はすべて timestamptz。列挙値は CHECK 制約で縛る。

-- ユーザー検索（F-44）の部分一致に使う
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ユーザー・認証 -------------------------------------------------------------

CREATE TABLE users (
    id                uuid         PRIMARY KEY DEFAULT uuidv7(),
    email             varchar(254) NOT NULL,
    -- Google ログインだけのユーザーは NULL
    password_hash     varchar(100),
    email_verified_at timestamptz,
    handle            varchar(15)  NOT NULL,
    display_name      varchar(50)  NOT NULL,
    bio               varchar(160) NOT NULL DEFAULT '',
    avatar_key        varchar(255),
    role              varchar(10)  NOT NULL DEFAULT 'USER',
    status            varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT users_handle_format_check CHECK (handle ~ '^[A-Za-z0-9_]{3,15}$')
);

-- メールアドレスとユーザーIDは大文字小文字を区別せずに一意にする
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
CREATE UNIQUE INDEX users_handle_lower_key ON users (lower(handle));
-- ユーザー検索（ILIKE による部分一致）用
CREATE INDEX users_handle_trgm_idx ON users USING gin (handle gin_trgm_ops);
CREATE INDEX users_display_name_trgm_idx ON users USING gin (display_name gin_trgm_ops);

CREATE TABLE auth_providers (
    id               uuid         PRIMARY KEY DEFAULT uuidv7(),
    user_id          uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         varchar(20)  NOT NULL,
    provider_user_id varchar(255) NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT auth_providers_provider_check CHECK (provider IN ('GOOGLE')),
    CONSTRAINT auth_providers_provider_user_key UNIQUE (provider, provider_user_id)
);
CREATE INDEX auth_providers_user_id_idx ON auth_providers (user_id);

-- トークン類は平文を保存せず、SHA-256 のハッシュ（16 進 64 文字）だけを保存する
CREATE TABLE refresh_tokens (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

CREATE TABLE email_verifications (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT email_verifications_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX email_verifications_user_id_idx ON email_verifications (user_id);

CREATE TABLE password_resets (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT password_resets_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX password_resets_user_id_idx ON password_resets (user_id);

-- 投稿 -----------------------------------------------------------------------

CREATE TABLE posts (
    id         uuid         PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 画像があれば本文なしでも投稿できる（F-10）ので空文字を許す
    body       varchar(280) NOT NULL DEFAULT '',
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX posts_user_id_created_at_idx ON posts (user_id, created_at DESC);
CREATE INDEX posts_created_at_idx ON posts (created_at DESC);

CREATE TABLE post_images (
    id            uuid         PRIMARY KEY DEFAULT uuidv7(),
    post_id       uuid         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    original_key  varchar(255) NOT NULL,
    thumbnail_key varchar(255) NOT NULL,
    width         integer      NOT NULL,
    height        integer      NOT NULL,
    sort_order    smallint     NOT NULL,
    -- 1 投稿につき 4 枚まで（sort_order 0〜3 を一意にする）
    CONSTRAINT post_images_sort_order_check CHECK (sort_order BETWEEN 0 AND 3),
    CONSTRAINT post_images_post_sort_order_key UNIQUE (post_id, sort_order),
    CONSTRAINT post_images_size_check CHECK (width > 0 AND height > 0)
);

CREATE TABLE comments (
    id         uuid         PRIMARY KEY DEFAULT uuidv7(),
    post_id    uuid         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- 退会時、返信が付いたコメントは「削除されたユーザー」として残す（F-06）ため、
    -- ユーザーの削除では連鎖削除せず NULL にする。返信のないコメントはアプリ側で先に削除する
    user_id    uuid         REFERENCES users (id) ON DELETE SET NULL,
    parent_id  uuid         REFERENCES comments (id) ON DELETE CASCADE,
    body       varchar(280) NOT NULL,
    -- 返信が付いたコメントの削除は論理削除（F-33）
    deleted_at timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX comments_post_id_created_at_idx ON comments (post_id, created_at);
CREATE INDEX comments_parent_id_idx ON comments (parent_id);
CREATE INDEX comments_user_id_idx ON comments (user_id);

-- リアクション・関係 ---------------------------------------------------------

CREATE TABLE likes (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    uuid        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);
-- いいね数の集計用
CREATE INDEX likes_post_id_idx ON likes (post_id);

CREATE TABLE follows (
    follower_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT follows_not_self_check CHECK (follower_id <> followee_id)
);
-- フォロワー一覧用
CREATE INDEX follows_followee_id_idx ON follows (followee_id);

CREATE TABLE blocks (
    blocker_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self_check CHECK (blocker_id <> blocked_id)
);
-- 「相手からブロックされているか」の確認用
CREATE INDEX blocks_blocked_id_idx ON blocks (blocked_id);

-- 通知・通報 -----------------------------------------------------------------

CREATE TABLE notifications (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    recipient_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         varchar(10) NOT NULL,
    post_id      uuid        REFERENCES posts (id) ON DELETE CASCADE,
    comment_id   uuid        REFERENCES comments (id) ON DELETE CASCADE,
    read_at      timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notifications_type_check CHECK (type IN ('LIKE', 'COMMENT', 'REPLY', 'FOLLOW'))
);
CREATE INDEX notifications_recipient_created_at_idx ON notifications (recipient_id, created_at DESC);
-- 未読数（F-51）の集計用
CREATE INDEX notifications_unread_idx ON notifications (recipient_id) WHERE read_at IS NULL;

CREATE TABLE reports (
    id          uuid          PRIMARY KEY DEFAULT uuidv7(),
    -- 通報者が退会しても、管理側の対応履歴として通報は残す
    reporter_id uuid          REFERENCES users (id) ON DELETE SET NULL,
    target_type varchar(10)   NOT NULL,
    -- 対象は投稿・コメント・ユーザーのいずれかなので外部キーは張らない
    target_id   uuid          NOT NULL,
    reason      varchar(20)   NOT NULL,
    detail      varchar(1000) NOT NULL DEFAULT '',
    status      varchar(10)   NOT NULL DEFAULT 'OPEN',
    created_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT reports_target_type_check CHECK (target_type IN ('POST', 'COMMENT', 'USER')),
    CONSTRAINT reports_reason_check CHECK (reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE_IMAGE', 'OTHER')),
    CONSTRAINT reports_status_check CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED'))
);
CREATE INDEX reports_status_created_at_idx ON reports (status, created_at);
