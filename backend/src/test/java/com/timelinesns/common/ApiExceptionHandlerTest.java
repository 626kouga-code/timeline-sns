package com.timelinesns.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 例外から API のエラー応答（application/problem+json）への変換（ApiExceptionHandler）。
 * Spring のコンテキストは起動せず、テスト用のコントローラーで各例外を起こして応答の形を確かめる。
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    record Input(@NotBlank(message = "名前を入力してください") String name,
            @NotBlank(message = "本文を入力してください") String body) {
    }

    @RestController
    static class FailingController {

        @GetMapping("/api-exception")
        String apiException() {
            throw new ApiException(HttpStatus.CONFLICT, "HANDLE_TAKEN", "このユーザーIDは既に使われています", "handle");
        }

        @GetMapping("/api-exception-without-field")
        String apiExceptionWithoutField() {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作を行う権限がありません");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody Input input) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/query")
        String query(@RequestParam int limit) {
            return "ok";
        }

        @PostMapping("/upload")
        String upload(@RequestPart("file") MultipartFile file) {
            return "ok";
        }

        @GetMapping("/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(5 * 1024 * 1024);
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("internal detail: db password=xxx");
        }
    }

    @Test
    void unexpectedExceptionIsLoggedAndBecomesInternalErrorWithRequestId(CapturedOutput output) throws Exception {
        MockMvc withRequestId = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();

        MvcResult result = withRequestId.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("サーバーでエラーが発生しました。時間をおいてもう一度お試しください"))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"requestId\":\"" + requestId + "\"")
                .doesNotContain("internal detail");
        // 原因を調べられるよう、ログにはスタックトレースが出る
        assertThat(output).contains("ERROR").contains("IllegalStateException: internal detail");
    }

    @Test
    void apiExceptionBecomesProblemWithCodeAndFieldError() throws Exception {
        mvc.perform(get("/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"))
                .andExpect(jsonPath("$.detail").value("このユーザーIDは既に使われています"))
                .andExpect(jsonPath("$.errors.handle").value("このユーザーIDは既に使われています"));
    }

    @Test
    void apiExceptionWithoutFieldHasNoErrors() throws Exception {
        mvc.perform(get("/api-exception-without-field"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void uniqueConstraintViolationBecomesConflictWithoutInternalMessage() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("データが競合しました。もう一度お試しください"));
    }

    @Test
    void invalidBodyListsEachFieldError() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"body\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("入力内容に誤りがあります"))
                .andExpect(jsonPath("$.errors", aMapWithSize(2)))
                .andExpect(jsonPath("$.errors.name").value("名前を入力してください"))
                .andExpect(jsonPath("$.errors.body").value("本文を入力してください"));
    }

    @Test
    void malformedPathIdIsNotFound() throws Exception {
        mvc.perform(get("/items/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void malformedQueryValueIsBadRequestWithFieldError() throws Exception {
        mvc.perform(get("/query").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.limit").value("値の形式が正しくありません"));
    }

    @Test
    void missingQueryValueIsBadRequest() throws Exception {
        mvc.perform(get("/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limit").value("値を指定してください"));
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mvc.perform(multipart("/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").value("ファイルを選んでください"));
    }

    @Test
    void uploadOverLimitIsImageTooLarge() throws Exception {
        mvc.perform(get("/too-large"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"))
                .andExpect(jsonPath("$.detail").value("画像は 5MB 以内にしてください"));
    }
}
