package com.timelinesns.user;

import com.timelinesns.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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
        String subject = jwt.getSubject();
        if (subject == null) {
            throw userNotFound();
        }
        // トークンは有効でも、発行後に退会したユーザーは存在しない
        User user = users.findById(UUID.fromString(subject)).orElseThrow(MeController::userNotFound);
        return MeResponse.from(user);
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ユーザーが見つかりません");
    }
}
