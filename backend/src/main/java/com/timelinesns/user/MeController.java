package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import com.timelinesns.common.Viewer;
import com.timelinesns.storage.MediaUrls;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;
    private final AvatarService avatars;
    private final MediaUrls mediaUrls;

    public MeController(UserRepository users, AvatarService avatars, MediaUrls mediaUrls) {
        this.users = users;
        this.avatars = avatars;
        this.mediaUrls = mediaUrls;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(currentUser(jwt), mediaUrls);
    }

    /**
     * プロフィール編集（F-41）。変更後の本人の情報を返す。
     */
    @PatchMapping
    @Transactional
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        User user = currentUser(jwt);
        user.updateProfile(request.displayName(), request.bio());
        return MeResponse.from(user, mediaUrls);
    }

    /**
     * アイコン画像の変更（F-41）。画面で正方形に切り抜いた画像を multipart の {@code file} で受け取る。
     */
    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse changeAvatar(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file)
            throws IOException {
        return avatars.change(Viewer.id(jwt), file.getBytes());
    }

    @DeleteMapping("/avatar")
    public MeResponse removeAvatar(@AuthenticationPrincipal Jwt jwt) {
        return avatars.remove(Viewer.id(jwt));
    }

    // トークンは有効でも、発行後に退会したユーザーは存在しない
    private User currentUser(Jwt jwt) {
        return users.findById(Viewer.id(jwt)).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません"));
    }
}
