package com.timelinesns.auth;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/**
 * API 仕様書（OpenAPI）の設定。仕様書は springdoc がコントローラーから作り、docs/openapi.json に残す
 * （OpenApiSpecTest）。springdoc を有効にしているのはローカルとテストだけ（application.yml）。
 * <ul>
 *   <li>認証はアクセストークン（JWT）を Authorization: Bearer で送る。既定ではすべての API に必要とする</li>
 *   <li>SecurityConfig でログインなしを許可している API は、仕様書でも認証なし・任意にする</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("timeline-sns API")
                        .version("v1")
                        .description("タイムライン型 SNS の API。概要は docs/06_api.md を参照"))
                // 実行環境で URL が変わると docs/openapi.json に差分が出るので、同じオリジンに固定する
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("ログイン API で受け取ったアクセストークン")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer publicEndpointsCustomizer() {
        AntPathMatcher matcher = new AntPathMatcher();
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (matchesAny(matcher, SecurityConfig.PUBLIC_POST_PATHS, path)) {
                setSecurity(item.getPost(), List.of());
            }
            if (matchesAny(matcher, SecurityConfig.GUEST_GET_PATHS, path)) {
                // 空の要件を並べると「認証なしでも、トークン付きでもよい」の意味になる
                setSecurity(item.getGet(), List.of(new SecurityRequirement(),
                        new SecurityRequirement().addList(BEARER_AUTH)));
            }
        });
    }

    // 一覧 API はどれも同じカーソル方式なので、パラメーターの説明をまとめて付ける
    @Bean
    OpenApiCustomizer pagingParametersCustomizer() {
        Map<String, String> descriptions = Map.of(
                "cursor", "前のページの最後の id（応答の nextCursor）。省略すると先頭から",
                "limit", "1 ページの件数（1〜50）");
        return openApi -> openApi.getPaths().values().forEach(item -> item.readOperations().forEach(operation -> {
            if (operation.getParameters() != null) {
                operation.getParameters().stream()
                        .filter(parameter -> parameter.getDescription() == null)
                        .forEach(parameter -> parameter.setDescription(descriptions.get(parameter.getName())));
            }
        }));
    }

    private static boolean matchesAny(AntPathMatcher matcher, List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private static void setSecurity(Operation operation, List<SecurityRequirement> security) {
        if (operation != null) {
            operation.setSecurity(security);
        }
    }
}
