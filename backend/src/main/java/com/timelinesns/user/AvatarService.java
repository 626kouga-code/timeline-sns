package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import com.timelinesns.image.ImageProcessor;
import com.timelinesns.image.ImageProcessor.ProcessedImage;
import com.timelinesns.storage.MediaUrls;
import com.timelinesns.storage.ObjectStorage;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * アイコン画像の変更・削除（F-41）。
 *
 * <p>画像は保存のたびに新しいキーで保存し（同じ URL の中身は変わらないので、ブラウザに長くキャッシュさせられる）、
 * DB の更新が確定してから古い画像を消す。DB の更新が取り消されたら、新しく保存した画像の方を消す。
 */
@Service
public class AvatarService {

    /** 保存するアイコンの一辺（px） */
    static final int SIZE = 400;

    private final UserRepository users;
    private final ImageProcessor images;
    private final ObjectStorage storage;
    private final MediaUrls mediaUrls;

    public AvatarService(UserRepository users, ImageProcessor images, ObjectStorage storage, MediaUrls mediaUrls) {
        this.users = users;
        this.images = images;
        this.storage = storage;
        this.mediaUrls = mediaUrls;
    }

    @Transactional
    public MeResponse change(UUID userId, byte[] upload) {
        User user = find(userId);
        ProcessedImage image = images.squareThumbnail(upload, SIZE);
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + image.extension();
        storage.put(key, image.content(), image.contentType());
        replace(user, key);
        return MeResponse.from(user, mediaUrls);
    }

    @Transactional
    public MeResponse remove(UUID userId) {
        User user = find(userId);
        replace(user, null);
        return MeResponse.from(user, mediaUrls);
    }

    private void replace(User user, String newKey) {
        String oldKey = user.changeAvatar(newKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // 確定したら古い画像が、取り消されたら新しい画像が使われなくなる
                String unused = status == STATUS_COMMITTED ? oldKey : newKey;
                if (unused != null) {
                    storage.deleteQuietly(unused);
                }
            }
        });
    }

    // トークンは有効でも、発行後に退会したユーザーは存在しない
    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません"));
    }
}
