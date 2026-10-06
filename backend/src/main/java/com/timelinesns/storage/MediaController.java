package com.timelinesns.storage;

import com.timelinesns.common.ApiException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保存した画像の配信（ゲストも見られる。SecurityConfig で GET /api/media/** を許可している）。
 * キーは保存のたびに新しく作り、同じキーの中身は変わらないので、ブラウザに長くキャッシュさせる。
 * 本番で CloudFront から直接配信するようにしたら、この API は使われなくなる（app.storage.public-base-url）。
 */
@RestController
public class MediaController {

    // 自分で作るキー（avatars/<UUID>/<UUID>.jpg など）の形だけを受け付ける
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.[a-z]+$");
    private static final Duration MAX_AGE = Duration.ofDays(365);

    private final ObjectStorage storage;

    public MediaController(ObjectStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/media/{*key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        // {*key} は先頭の / を含む
        String objectKey = key.startsWith("/") ? key.substring(1) : key;
        if (!KEY.matcher(objectKey).matches()) {
            throw notFound();
        }
        ObjectStorage.StoredObject object = storage.get(objectKey).orElseThrow(MediaController::notFound);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(object.content());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "指定されたデータは存在しないか、表示できません");
    }
}
