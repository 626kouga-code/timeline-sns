package com.timelinesns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 仕様書（docs/openapi.json）がコードと一致しているかの確認。
 * コントローラーや DTO を変えたら {@code ./gradlew test -PupdateOpenApi} で作り直してコミットする。
 */
@IntegrationTest
class OpenApiSpecTest {

    // テストは backend/ で動くので、リポジトリ直下の docs/ は一つ上
    private static final Path SPEC = Path.of("..", "docs", "openapi.json");

    @Autowired
    private MockMvc mvc;

    @Test
    void docsOpenApiJsonMatchesTheCode() throws Exception {
        String actual = normalize(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        if (Boolean.getBoolean("updateOpenApi")) {
            Files.writeString(SPEC, actual, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SPEC))
                .as("docs/openapi.json がありません。./gradlew test -PupdateOpenApi で作ってください")
                .isTrue();
        assertThat(normalize(Files.readString(SPEC, StandardCharsets.UTF_8)))
                .as("docs/openapi.json が古くなっています。./gradlew test -PupdateOpenApi で作り直してください")
                .isEqualTo(actual);
    }

    // Git の改行コード変換（CRLF）で差分扱いにならないように、改行を LF にそろえて末尾に 1 つ付ける
    private static String normalize(String json) {
        return json.replace("\r\n", "\n").strip() + "\n";
    }
}
