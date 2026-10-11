package com.timelinesns.user;

import static com.timelinesns.support.TestImages.image;
import static com.timelinesns.support.TestImages.read;
import static com.timelinesns.support.TestImages.resource;
import static com.timelinesns.support.TestImages.withOrientation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.timelinesns.auth.AccessTokenService;
import com.timelinesns.support.Fixtures;
import com.timelinesns.support.IntegrationTest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * アイコン画像の変更・削除（F-41）と画像の配信の結合テスト。保存先は Testcontainers の S3Mock。
 */
@IntegrationTest
class AvatarApiTest {

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
        alice = fixtures.user("alice");
    }

    @Nested
    class Upload {

        @Test
        void storesSquareJpegAndReturnsUrl() throws Exception {
            String url = avatarUrl(upload(image("jpeg", 800, 600, false), "photo.jpg")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").value(startsWith("/api/media/avatars/" + alice + "/")))
                    .andExpect(jsonPath("$.handle").value("alice")));

            MvcResult media = mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                    .andReturn();
            BufferedImage stored = read(media.getResponse().getContentAsByteArray());
            assertThat(stored.getWidth()).isEqualTo(AvatarService.SIZE);
            assertThat(stored.getHeight()).isEqualTo(AvatarService.SIZE);
        }

        // 小さい画像は拡大しない
        @Test
        void doesNotEnlargeSmallImages() throws Exception {
            String url = avatarUrl(upload(image("png", 120, 90, false), "small.png").andExpect(status().isOk()));
            BufferedImage stored = read(fetch(url));
            assertThat(stored.getWidth()).isEqualTo(90);
            assertThat(stored.getHeight()).isEqualTo(90);
        }

        @Test
        void keepsTransparencyAsPng() throws Exception {
            String url = avatarUrl(upload(image("png", 100, 100, true), "logo.png").andExpect(status().isOk()));
            assertThat(url).endsWith(".png");
            mvc.perform(get(url)).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
        }

        @Test
        void acceptsGifAndWebp() throws Exception {
            upload(image("gif", 50, 50, false), "anim.gif").andExpect(status().isOk());

            byte[] webp = resource("/images/red-blue.webp");
            String url = avatarUrl(upload(webp, "photo.webp").andExpect(status().isOk()));
            // 40×20（左が赤・右が青）の中央を正方形に切り抜くので、左が赤・右が青のまま 20×20 になる
            BufferedImage stored = read(fetch(url));
            assertThat(stored.getWidth()).isEqualTo(20);
            assertThat(new Color(stored.getRGB(2, 10)).getRed()).isGreaterThan(200);
            assertThat(new Color(stored.getRGB(17, 10)).getBlue()).isGreaterThan(200);
        }

        // Exif は残さない。ただし向き（Orientation）は画素に反映してから捨てる
        @Test
        void appliesExifOrientationAndStripsExif() throws Exception {
            // 横長（左が赤・右が青）の画像に「時計回りに 90 度回して表示する」（Orientation = 6）を付ける
            byte[] jpeg = withOrientation(image("jpeg", 200, 100, false), 6);
            assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).contains("Exif");

            byte[] stored = fetch(avatarUrl(upload(jpeg, "rotated.jpg").andExpect(status().isOk())));

            assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
            // 回すと縦長（上が赤・下が青）になり、その中央を切り抜くので上が赤・下が青になる
            BufferedImage image = read(stored);
            assertThat(image.getWidth()).isEqualTo(100);
            assertThat(new Color(image.getRGB(50, 10)).getRed()).isGreaterThan(200);
            assertThat(new Color(image.getRGB(50, 90)).getBlue()).isGreaterThan(200);
        }

        // 拡張子や Content-Type ではなく中身で判定する
        @Test
        void rejectsNonImageDisguisedAsJpeg() throws Exception {
            upload("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "evil.jpg")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        }

        @Test
        void rejectsBrokenImage() throws Exception {
            byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
            upload(header, "broken.jpg")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        }

        @Test
        void rejectsTooLargeFile() throws Exception {
            byte[] big = new byte[5 * 1024 * 1024 + 1];
            big[0] = (byte) 0xFF;
            big[1] = (byte) 0xD8;
            big[2] = (byte) 0xFF;
            upload(big, "big.jpg")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        }

        @Test
        void requiresFile() throws Exception {
            mvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                            .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                            .file(new MockMultipartFile("file", "a.png", "image/png", image("png", 10, 10, false))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/me/avatar")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ReplaceAndRemove {

        @Test
        void replacingDeletesOldImage() throws Exception {
            String first = avatarUrl(upload(image("jpeg", 100, 100, false), "a.jpg"));
            String second = avatarUrl(upload(image("jpeg", 100, 100, false), "b.jpg"));

            assertThat(second).isNotEqualTo(first);
            mvc.perform(get(first)).andExpect(status().isNotFound());
            mvc.perform(get(second)).andExpect(status().isOk());
        }

        @Test
        void removingClearsUrlAndDeletesImage() throws Exception {
            String url = avatarUrl(upload(image("jpeg", 100, 100, false), "a.jpg"));

            mvc.perform(delete("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
            mvc.perform(get(url)).andExpect(status().isNotFound());
        }

        // 失敗したアップロードでは今のアイコンを変えない
        @Test
        void failedUploadKeepsCurrentImage() throws Exception {
            String url = avatarUrl(upload(image("jpeg", 100, 100, false), "a.jpg"));
            upload("not an image".getBytes(StandardCharsets.UTF_8), "b.jpg").andExpect(status().isBadRequest());

            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                    .andExpect(jsonPath("$.avatarUrl").value(url));
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    // アイコンの URL は、作者・ユーザーを返すすべての応答に入る
    @Test
    void avatarUrlAppearsInProfilePostsAndComments() throws Exception {
        String url = avatarUrl(upload(image("jpeg", 100, 100, false), "a.jpg"));
        UUID post = fixtures.post(alice, "hello");
        fixtures.comment(alice, post, null, "comment");
        UUID bob = fixtures.user("bob");
        fixtures.follow(bob, alice);

        mvc.perform(get("/api/users/{handle}", "alice")).andExpect(jsonPath("$.avatarUrl").value(url));
        mvc.perform(get("/api/users/{handle}/followers", "alice"))
                .andExpect(jsonPath("$.items[0].avatarUrl").value(nullValue()));
        mvc.perform(get("/api/users/{handle}/following", "bob"))
                .andExpect(jsonPath("$.items[0].avatarUrl").value(url));
        mvc.perform(get("/api/timeline/global")).andExpect(jsonPath("$.items[0].author.avatarUrl").value(url));
        mvc.perform(get("/api/posts/{id}/comments", post)).andExpect(jsonPath("$[0].author.avatarUrl").value(url));
    }

    @Test
    void mediaReturns404ForUnknownOrMalformedKey() throws Exception {
        mvc.perform(get("/api/media/avatars/{id}/missing.jpg", alice)).andExpect(status().isNotFound());
        // 自分で作る形（拡張子つきの英数字のパス）でないキーは S3 に問い合わせない
        mvc.perform(get("/api/media/avatars/secret")).andExpect(status().isNotFound());
        // パスに .. を含む URL は、Spring Security のファイアウォールがコントローラーより前で弾く
        mvc.perform(get("/api/media/../secret.txt")).andExpect(status().isBadRequest());
    }

    private ResultActions upload(byte[] content, String filename) throws Exception {
        // Content-Type はわざと信用できない値（常に image/jpeg）にする。サーバーは中身で判定する
        return mvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                .file(new MockMultipartFile("file", filename, "image/jpeg", content))
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)));
    }

    private static String avatarUrl(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.avatarUrl");
    }

    private byte[] fetch(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
}
