package com.timelinesns.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエスト ID を振り、1 リクエストにつき 1 行のアクセスログを出す（docs/10 のログの出力方針）。
 * <ul>
 *   <li>リクエスト ID は MDC に入れて同じリクエストの全ログに付け、レスポンスの {@code X-Request-Id} でも返す</li>
 *   <li>認証（Spring Security）より前に置き、401・403 で終わるリクエストもアクセスログに残す</li>
 *   <li>パスにクエリ文字列は含めない。ヘルスチェック（/actuator/**）はログに出さない</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String USER_ID_KEY = "userId";

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // 外から受け取る ID は、ログを壊したり偽の行を混ぜたりできない文字と長さに限る
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator/")) {
                logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
            }
            MDC.remove(USER_ID_KEY);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private static String requestId(HttpServletRequest request) {
        String received = request.getHeader(REQUEST_ID_HEADER);
        if (received != null && VALID_REQUEST_ID.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }

    // テキスト形式のログでも読めるようにメッセージにも書き、JSON 形式では各値を項目として出す
    private static void logAccess(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        LOG.atInfo()
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log("{} {} {} {}ms", method, path, status, durationMs);
    }
}
