package com.timelinesns.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン中のリクエストなら、ユーザー ID を MDC に入れてログに付ける。
 * 認証が済んだ後に置く必要があるので、Bean にはせず SecurityConfig でフィルタチェーンに足す。
 * MDC から消すのは、アクセスログを出し終えた RequestLoggingFilter で行う。
 */
public class UserIdLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                && jwt.getSubject() != null) {
            MDC.put(RequestLoggingFilter.USER_ID_KEY, jwt.getSubject());
        }
        chain.doFilter(request, response);
    }
}
