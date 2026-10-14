package com.timelinesns.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * リクエスト ID の付与とアクセスログ（RequestLoggingFilter）。
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void generatesRequestIdAndKeepsItInMdcDuringRequest() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), response,
                new MockFilterChain(new HttpServlet() {
                }, (request, res, chain) -> seen.set(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY))));

        String header = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(header).matches("[0-9a-f-]{36}");
        assertThat(seen.get()).isEqualTo(header);
    }

    @Test
    void usesValidReceivedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void replacesReceivedRequestIdThatCouldBreakLogs() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "abc\nfake log line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
        }, (request, response, next) -> MDC.put(RequestLoggingFilter.USER_ID_KEY, "user-1"));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), new MockHttpServletResponse(), chain);

        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.USER_ID_KEY)).isNull();
    }

    @Test
    void logsOneAccessLineWithoutQueryString(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search/users");
        request.setQueryString("q=secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output).containsPattern("GET /api/search/users 404 \\d+ms");
        assertThat(output).doesNotContain("secret");
    }

    @Test
    void doesNotLogHealthChecks(CapturedOutput output) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(output).doesNotContain("/actuator/health");
    }
}
