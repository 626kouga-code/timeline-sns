package com.timelinesns.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
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
import com.timelinesns.support.QueryCounter;
import com.timelinesns.user.UserRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 画像投稿（F-11）の結合テスト。保存先は Testcontainers の S3Mock。
 */
@IntegrationTest
class PostImageApiTest {

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
    class Create {

        @Test
        void storesDisplayAndThumbnailKeepingAspectRatioAndOrder() throws Exception {
            String json = create("写真です", image("jpeg", 4000, 3000), image("png", 300, 600))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.body").value("写真です"))
                    .andExpect(jsonPath("$.images.length()").value(2))
                    // 表示用は長辺 2048px に縮小（縦横比はそのまま）。小さい画像は拡大しない
                    .andExpect(jsonPath("$.images[0].width").value(2048))
                    .andExpect(jsonPath("$.images[0].height").value(1536))
                    .andExpect(jsonPath("$.images[1].width").value(300))
                    .andExpect(jsonPath("$.images[1].height").value(600))
                    .andExpect(jsonPath("$.images[0].url").value(endsWith(".jpg")))
                    .andReturn().getResponse().getContentAsString();

            BufferedImage display = read(fetch(JsonPath.read(json, "$.images[0].url")));
            BufferedImage thumbnail = read(fetch(JsonPath.read(json, "$.images[0].thumbnailUrl")));
            assertThat(display.getWidth()).isEqualTo(2048);
            assertThat(thumbnail.getWidth()).isEqualTo(640);
            assertThat(thumbnail.getHeight()).isEqualTo(480);
            BufferedImage smallThumbnail = read(fetch(JsonPath.read(json, "$.images[1].thumbnailUrl")));
            assertThat(smallThumbnail.getHeight()).isEqualTo(600);
            assertThat(smallThumbnail.getWidth()).isEqualTo(300);
        }

        // 画像があれば本文なしでも投稿できる（F-10）
        @Test
        void allowsImageOnlyPost() throws Exception {
            create(null, image("png", 50, 50))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.body").value(""))
                    .andExpect(jsonPath("$.images.length()").value(1));
        }

        @Test
        void rejectsPostWithoutBodyOrImages() throws Exception {
            create("   ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").value("本文を入力するか、画像を選んでください"));
        }

        @Test
        void rejectsMoreThanFourImages() throws Exception {
            byte[] png = image("png", 10, 10);
            create("多すぎ", png, png, png, png, png)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.images").value("画像は 4 枚までです"));
            assertThat(count("posts")).isZero();
        }

        // 1 枚でも形式を偽ったファイルがあれば、投稿も画像も保存しない
        @Test
        void rejectsDisguisedFileWithoutStoringAnything() throws Exception {
            create("混ざっている", image("png", 10, 10), "not an image".getBytes(StandardCharsets.UTF_8))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
            assertThat(count("posts")).isZero();
            assertThat(count("post_images")).isZero();
        }

        @Test
        void stripsExif() throws Exception {
            byte[] jpeg = withExifComment(image("jpeg", 100, 100));
            assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).contains("Exif");

            String json = create(null, jpeg).andReturn().getResponse().getContentAsString();

            assertThat(new String(fetch(JsonPath.read(json, "$.images[0].url")), StandardCharsets.ISO_8859_1))
                    .doesNotContain("Exif");
            assertThat(new String(fetch(JsonPath.read(json, "$.images[0].thumbnailUrl")), StandardCharsets.ISO_8859_1))
                    .doesNotContain("Exif");
        }

        // GIF は表示用をアニメーションのまま残し、サムネイルは 1 枚目の静止画にする
        @Test
        void keepsGifAnimationForDisplay() throws Exception {
            byte[] gif = animatedGif(3);
            String json = create(null, gif)
                    .andExpect(jsonPath("$.images[0].url").value(endsWith(".gif")))
                    .andReturn().getResponse().getContentAsString();

            String url = JsonPath.read(json, "$.images[0].url");
            mvc.perform(get(url)).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/gif"));
            assertThat(frames(fetch(url))).isEqualTo(3);
            assertThat((String) JsonPath.read(json, "$.images[0].thumbnailUrl")).doesNotEndWith(".gif");
        }
    }

    @Test
    void imagesAppearInTimelineAndDetail() throws Exception {
        String json = create("タイムライン", image("png", 20, 20), image("jpeg", 30, 30))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mvc.perform(get("/api/timeline/global"))
                .andExpect(jsonPath("$.items[0].images.length()").value(2))
                .andExpect(jsonPath("$.items[0].images[1].width").value(30));
        mvc.perform(get("/api/posts/{id}", id)).andExpect(jsonPath("$.images.length()").value(2));
        mvc.perform(get("/api/users/{handle}/posts", "alice"))
                .andExpect(jsonPath("$.items[0].images.length()").value(2));
    }

    @Test
    void textPostHasEmptyImages() throws Exception {
        fixtures.post(alice, "文字だけ");
        mvc.perform(get("/api/timeline/global")).andExpect(jsonPath("$.items[0].images.length()").value(0));
    }

    @Test
    void deletingPostDeletesStoredImages() throws Exception {
        String json = create("消す", image("png", 20, 20)).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(json, "$.images[0].url");
        String thumbnailUrl = JsonPath.read(json, "$.images[0].thumbnailUrl");

        mvc.perform(delete("/api/posts/{id}", (String) JsonPath.read(json, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(thumbnailUrl)).andExpect(status().isNotFound());
        assertThat(count("post_images")).isZero();
    }

    // N+1 の回帰テスト。画像の有無・枚数・投稿の件数で、タイムラインの SQL の数は変わらない
    @Test
    void timelineUsesSameNumberOfQueriesRegardlessOfImages() throws Exception {
        fixtures.post(alice, "文字だけ");
        long none = QueryCounter.count(() -> mvc.perform(get("/api/timeline/global")).andExpect(status().isOk()));

        byte[] png = image("png", 10, 10);
        for (int i = 0; i < 5; i++) {
            create("画像 " + i, png, png, png, png).andExpect(status().isCreated());
        }
        long many = QueryCounter.count(() -> mvc.perform(get("/api/timeline/global"))
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].images.length()").value(4)));

        assertThat(none).isPositive();
        assertThat(many).isEqualTo(none);
    }

    private ResultActions create(String body, byte[]... images) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/posts");
        if (body != null) {
            request.param("body", body);
        }
        for (int i = 0; i < images.length; i++) {
            // Content-Type はわざと信用できない値にする。サーバーは中身で判定する
            request.file(new MockMultipartFile("images", "image" + i + ".jpg", "image/jpeg", images[i]));
        }
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixtures.bearer(alice)));
    }

    private byte[] fetch(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(bytes)));
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.ORANGE);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    // コマ数 frames の GIF アニメーション（コマごとに色を変える）
    private static byte[] animatedGif(int frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (Color color : List.of(Color.RED, Color.GREEN, Color.BLUE).subList(0, frames)) {
                BufferedImage frame = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = frame.createGraphics();
                g.setColor(color);
                g.fillRect(0, 0, 40, 30);
                g.dispose();
                writer.writeToSequence(new IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static int frames(byte[] gif) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            ImageReader reader = ImageIO.getImageReaders(in).next();
            try {
                reader.setInput(in);
                return reader.getNumImages(true);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * JPEG の先頭（SOI の直後）に、Orientation（= 1、そのまま）だけを持つ Exif（APP1）を差し込む。
     */
    private static byte[] withExifComment(byte[] jpeg) {
        byte[] app1 = {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        };
        byte[] out = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, out, 0, 2);
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }
}
