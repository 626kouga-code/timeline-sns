package com.timelinesns.post;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import com.timelinesns.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 投稿の OGP（F-14）の結合テスト。サイトの URL はテストでも既定値（http://localhost:5173）。
 */
@IntegrationTest
class OgpApiTest {

    private static final String SITE = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccessTokenService accessTokens;

    private Fixtures fixtures;
    private UUID alice;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users CASCADE");
        fixtures = new Fixtures(jdbc, users, accessTokens);
        alice = fixtures.user("alice", "アリス");
    }

    @Test
    void returnsHtmlWithTitleDescriptionAndUrlForGuests() throws Exception {
        UUID post = fixtures.post(alice, "はじめての\n\n投稿です");

        mvc.perform(get("/api/ogp/posts/{id}", post))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:title\" content=\"アリス（@alice）\">")))
                // 改行は空白 1 つにまとめる
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"はじめての 投稿です\">")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:url\" content=\"" + SITE + "/posts/" + post + "\">")))
                .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary\">")))
                .andExpect(content().string(not(containsString("og:image"))))
                // 人が開いたときは画面の投稿詳細へ移る
                .andExpect(content().string(containsString(
                        "<meta http-equiv=\"refresh\" content=\"0; url=/posts/" + post + "\">")));
    }

    @Test
    void usesFirstImageAsAbsoluteUrlWithLargeCard() throws Exception {
        UUID post = fixtures.post(alice, "写真");
        fixtures.image(post, 1, "posts/second.webp", "posts/second-thumb.webp", 800, 600);
        fixtures.image(post, 0, "posts/first.webp", "posts/first-thumb.webp", 1200, 900);

        mvc.perform(get("/api/ogp/posts/{id}", post))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<meta property=\"og:image\" content=\"" + SITE + "/api/media/posts/first.webp\">")))
                .andExpect(content().string(containsString("<meta property=\"og:image:width\" content=\"1200\">")))
                .andExpect(content().string(containsString("<meta property=\"og:image:height\" content=\"900\">")))
                .andExpect(content().string(containsString(
                        "<meta name=\"twitter:card\" content=\"summary_large_image\">")));
    }

    @Test
    void describesImageOnlyPost() throws Exception {
        UUID post = fixtures.post(alice, "");
        fixtures.image(post, 0, "posts/a.webp", "posts/a-thumb.webp", 100, 100);
        fixtures.image(post, 1, "posts/b.webp", "posts/b-thumb.webp", 100, 100);

        mvc.perform(get("/api/ogp/posts/{id}", post))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"2 枚の画像\">")));
    }

    @Test
    void cutsLongBodyByCharacters() throws Exception {
        // 絵文字（サロゲートペア）を含めても、100 文字目で切る
        String body = "😀".repeat(OgpController.DESCRIPTION_LENGTH + 20);
        UUID post = fixtures.post(alice, body);

        mvc.perform(get("/api/ogp/posts/{id}", post))
                .andExpect(content().string(containsString(
                        "content=\"" + "😀".repeat(OgpController.DESCRIPTION_LENGTH) + "…\"")));
    }

    @Test
    void escapesHtmlInBodyAndName() throws Exception {
        UUID mallory = fixtures.user("mallory", "<b>\"悪い\"</b>");
        UUID post = fixtures.post(mallory, "<script>alert('x')</script> & more");

        mvc.perform(get("/api/ogp/posts/{id}", post))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString(
                        "content=\"&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; more\"")))
                .andExpect(content().string(containsString(
                        "content=\"&lt;b&gt;&quot;悪い&quot;&lt;/b&gt;（@mallory）\"")));
    }

    @Test
    void returns404HtmlForMissingSuspendedOrMalformedPost() throws Exception {
        UUID post = fixtures.post(alice, "凍結される人の投稿");
        fixtures.suspend(alice);

        for (String id : new String[] {post.toString(), UUID.randomUUID().toString(), "not-a-uuid"}) {
            mvc.perform(get("/api/ogp/posts/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString(
                            "<meta property=\"og:site_name\" content=\"timeline\">")))
                    .andExpect(content().string(not(containsString("凍結される人の投稿"))));
        }
    }
}
