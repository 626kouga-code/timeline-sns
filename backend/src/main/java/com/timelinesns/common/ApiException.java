package com.timelinesns.common;

import org.springframework.http.HttpStatus;

/**
 * API のエラー応答にそのまま変換する例外。{@link ApiExceptionHandler} が ProblemDetail（RFC 9457）にする。
 * {@code code} はフロントが分岐に使う機械向けの識別子、{@code message} は画面に出せる日本語の説明。
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final String field;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    /**
     * 特定の入力項目に関するエラー。応答の {@code errors} に項目名つきで入る。
     */
    public ApiException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
