package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import com.timelinesns.common.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(currentUser(jwt));
    }

    /**
     * プロフィール編集（F-41）。変更後の本人の情報を返す。
     */
    @PatchMapping
    @Transactional
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        User user = currentUser(jwt);
        user.updateProfile(request.displayName(), request.bio());
        return MeResponse.from(user);
    }

    // トークンは有効でも、発行後に退会したユーザーは存在しない
    private User currentUser(Jwt jwt) {
        return users.findById(Viewer.id(jwt)).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません"));
    }
}
