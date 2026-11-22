package com.timelinesns.post;

import com.timelinesns.common.SiteProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * 投稿の OGP（F-14）。投稿の URL を SNS に貼ったときのプレビュー（本文の冒頭と 1 枚目の画像）に使う HTML を返す。
 *
 * <p>SNS のクローラーは JavaScript を実行しないので、画面（SPA の index.html）からは投稿ごとの内容を読めない。
 * 本番では CloudFront Functions が、クローラーからの {@code /posts/{id}} をここ（{@code /api/ogp/posts/{id}}）に回す
 * （docs/12_aws-architecture.md）。人がこの URL を開いたときは、画面の {@code /posts/{id}} にすぐ移る。
 */
@Tag(name = "OGP")
@RestController
public class OgpController {

    static final String SITE_NAME = "timeline";

    /** og:description に入れる本文の長さ（文字数） */
    static final int DESCRIPTION_LENGTH = 100;

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final PostQuery posts;
    private final SiteProperties site;

    public OgpController(PostQuery posts, SiteProperties site) {
        this.posts = posts;
        this.site = site;
    }

    // ゲストも取得できる（SecurityConfig で GET /api/ogp/** を許可している）
    @Operation(summary = "投稿の OGP", description = "SNS のクローラー向けに、OGP の meta タグを入れた HTML を返す。"
            + "人が開いたときは投稿の画面へ移る")
    @ApiResponse(responseCode = "404", description = "投稿が存在しないか、表示できない（サイト名だけの HTML を返す）")
    @GetMapping(value = "/api/ogp/posts/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> post(@PathVariable String id) {
        // ID の形が正しくないときも、JSON のエラーではなく HTML の 404 にする
        Optional<PostResponse> post = parseId(id).flatMap(postId -> posts.findVisible(postId, null));
        return post.map(found -> ResponseEntity.ok().contentType(HTML).body(postPage(found)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).body(notFoundPage()));
    }

    private String postPage(PostResponse post) {
        String path = "/posts/" + post.id();
        String title = post.author().displayName() + "（@" + post.author().handle() + "）";
        String description = description(post);
        StringBuilder meta = new StringBuilder()
                .append(tag("name", "description", description))
                .append(tag("property", "og:site_name", SITE_NAME))
                .append(tag("property", "og:type", "article"))
                .append(tag("property", "og:title", title))
                .append(tag("property", "og:description", description))
                .append(tag("property", "og:url", site.absolute(path)));
        if (post.images().isEmpty()) {
            meta.append(tag("name", "twitter:card", "summary"));
        } else {
            PostResponse.Image image = post.images().getFirst();
            meta.append(tag("property", "og:image", site.absolute(image.url())))
                    .append(tag("property", "og:image:width", String.valueOf(image.width())))
                    .append(tag("property", "og:image:height", String.valueOf(image.height())))
                    .append(tag("name", "twitter:card", "summary_large_image"));
        }
        return page(title + " - " + SITE_NAME, meta.toString(), path);
    }

    private String notFoundPage() {
        String meta = tag("property", "og:site_name", SITE_NAME) + tag("property", "og:title", SITE_NAME);
        return page(SITE_NAME, meta, "/");
    }

    /** 本文の冒頭。改行などの空白は 1 つの空白にまとめ、長ければ切って「…」を付ける。画像だけの投稿は本文が空になる */
    static String description(PostResponse post) {
        String body = post.body().strip().replaceAll("\\s+", " ");
        if (body.isEmpty()) {
            return post.images().size() + " 枚の画像";
        }
        if (body.codePointCount(0, body.length()) <= DESCRIPTION_LENGTH) {
            return body;
        }
        // 絵文字などのサロゲートペアの途中で切らないよう、文字（コードポイント）単位で数える
        return body.substring(0, body.offsetByCodePoints(0, DESCRIPTION_LENGTH)) + "…";
    }

    private static String page(String title, String meta, String path) {
        String href = escape(path);
        return "<!doctype html>\n"
                + "<html lang=\"ja\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + meta
                + "<meta http-equiv=\"refresh\" content=\"0; url=" + href + "\">\n"
                + "</head>\n"
                + "<body><p><a href=\"" + href + "\">" + escape(SITE_NAME) + " で開く</a></p></body>\n"
                + "</html>\n";
    }

    private static String tag(String attribute, String name, String content) {
        return "<meta " + attribute + "=\"" + name + "\" content=\"" + escape(content) + "\">\n";
    }

    // 本文・表示名に < や " が入っていても HTML が壊れない（タグとして解釈されない）ようにする
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static Optional<UUID> parseId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
