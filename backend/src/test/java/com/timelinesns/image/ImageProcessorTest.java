package com.timelinesns.image;

import static com.timelinesns.support.TestImages.animatedGif;
import static com.timelinesns.support.TestImages.frames;
import static com.timelinesns.support.TestImages.image;
import static com.timelinesns.support.TestImages.read;
import static com.timelinesns.support.TestImages.resource;
import static com.timelinesns.support.TestImages.threeColumns;
import static com.timelinesns.support.TestImages.withOrientation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timelinesns.common.ApiException;
import com.timelinesns.image.ImageProcessor.DecodedImage;
import com.timelinesns.image.ImageProcessor.ProcessedImage;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 画像の作り直し（ImageProcessor）。実際に画像を作って処理し、出力した画像を読み直して確かめる。
 */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static Color pixel(byte[] image, int x, int y) {
        return new Color(read(image).getRGB(x, y), true);
    }

    private static boolean isRed(Color c) {
        return c.getRed() > 200 && c.getGreen() < 60 && c.getBlue() < 60;
    }

    private static boolean isBlue(Color c) {
        return c.getBlue() > 200 && c.getRed() < 60 && c.getGreen() < 60;
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Nested
    class FormatDetection {

        @ParameterizedTest
        @ValueSource(strings = {"jpeg", "png", "gif"})
        void acceptsSupportedFormats(String format) {
            DecodedImage decoded = processor.decode(image(format, 30, 20));

            assertThat(decoded.width()).isEqualTo(30);
            assertThat(decoded.height()).isEqualTo(20);
            assertThat(decoded.isGif()).isEqualTo(format.equals("gif"));
        }

        @Test
        void acceptsWebp() {
            DecodedImage decoded = processor.decode(resource("/images/red-blue.webp"));

            assertThat(decoded.isGif()).isFalse();
            assertThat(decoded.width()).isPositive();
            assertThat(decoded.height()).isPositive();
        }

        @Test
        void rejectsFormatsOtherThanJpegPngWebpGif() {
            // BMP は ImageIO で読めるが、受け付けない
            assertThatThrownBy(() -> processor.decode(image("bmp", 10, 10)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_IMAGE"));
        }

        @Test
        void rejectsNonImageBytes() {
            assertThatThrownBy(() -> processor.decode("not an image".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_IMAGE"));
        }

        @Test
        void rejectsBrokenFileWithValidHeader() {
            // 先頭だけ JPEG に見せかけた壊れたファイル
            byte[] fake = Arrays.copyOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}, 100);

            assertThatThrownBy(() -> processor.decode(fake))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_IMAGE"));
        }

        @Test
        void rejectsEmptyInput() {
            assertThatThrownBy(() -> processor.decode(new byte[0])).isInstanceOf(ApiException.class);
        }
    }

    @Nested
    class Limits {

        @Test
        void rejectsFileOverFiveMegabytes() {
            byte[] tooLarge = Arrays.copyOf(image("png", 10, 10), ImageProcessor.MAX_BYTES + 1);

            assertThatThrownBy(() -> processor.decode(tooLarge))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("IMAGE_TOO_LARGE"));
        }

        @Test
        void rejectsTooManyPixelsBeforeDecoding() {
            // ヘッダで 8000×8000（6,400 万画素）を名乗る小さな PNG。展開する前に弾く
            assertThatThrownBy(() -> processor.decode(pngHeaderOnly(8000, 8000)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("IMAGE_TOO_LARGE"));
        }

        private static byte[] pngHeaderOnly(int width, int height) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            ByteBuffer ihdr = ByteBuffer.allocate(17).put("IHDR".getBytes(StandardCharsets.US_ASCII))
                    .putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0)
                    .put((byte) 0);
            CRC32 crc = new CRC32();
            crc.update(ihdr.array());
            out.writeBytes(ByteBuffer.allocate(4).putInt(13).array());
            out.writeBytes(ihdr.array());
            out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
            return out.toByteArray();
        }
    }

    @Nested
    class Fit {

        @Test
        void shrinksLongSideKeepingAspectRatio() {
            ProcessedImage display = processor.fit(processor.decode(image("png", 4000, 3000)), 2048);
            ProcessedImage thumbnail = processor.fit(processor.decode(image("png", 3000, 4000)), 640);

            assertThat(display.width()).isEqualTo(2048);
            assertThat(display.height()).isEqualTo(1536);
            assertThat(thumbnail.width()).isEqualTo(480);
            assertThat(thumbnail.height()).isEqualTo(640);
            // 応答に書く幅・高さと、実際の画像の大きさが一致する
            BufferedImage actual = read(display.content());
            assertThat(actual.getWidth()).isEqualTo(2048);
            assertThat(actual.getHeight()).isEqualTo(1536);
        }

        @Test
        void doesNotEnlargeSmallImage() {
            ProcessedImage processed = processor.fit(processor.decode(image("png", 300, 200)), 640);

            assertThat(processed.width()).isEqualTo(300);
            assertThat(processed.height()).isEqualTo(200);
        }

        @Test
        void keepsColorsWhenShrinking() {
            byte[] out = processor.fit(processor.decode(image("png", 1000, 500)), 100).content();

            assertThat(isRed(pixel(out, 10, 25))).isTrue();
            assertThat(isBlue(pixel(out, 90, 25))).isTrue();
        }

        @Test
        void decodedImageCanBeResizedSeveralTimes() {
            DecodedImage decoded = processor.decode(image("jpeg", 3000, 1500));

            assertThat(processor.fit(decoded, 2048).width()).isEqualTo(2048);
            assertThat(processor.fit(decoded, 640).width()).isEqualTo(640);
        }
    }

    @Nested
    class SquareThumbnail {

        @Test
        void cropsCenterSquareAndShrinks() {
            ProcessedImage square = processor.squareThumbnail(threeColumns("png", 900, 300), 100);

            assertThat(square.width()).isEqualTo(100);
            assertThat(square.height()).isEqualTo(100);
            // 中央（緑）だけが残る
            Color left = pixel(square.content(), 5, 50);
            Color right = pixel(square.content(), 95, 50);
            assertThat(left.getGreen()).isGreaterThan(200);
            assertThat(right.getGreen()).isGreaterThan(200);
            assertThat(left.getRed()).isLessThan(60);
            assertThat(right.getBlue()).isLessThan(60);
        }

        @Test
        void doesNotEnlargeSmallImage() {
            ProcessedImage square = processor.squareThumbnail(image("png", 120, 90), 400);

            assertThat(square.width()).isEqualTo(90);
            assertThat(square.height()).isEqualTo(90);
        }
    }

    @Nested
    class Encoding {

        @Test
        void opaqueImageBecomesJpeg() {
            ProcessedImage processed = processor.fit(processor.decode(image("png", 20, 20)), 640);

            assertThat(processed.contentType()).isEqualTo("image/jpeg");
            assertThat(processed.extension()).isEqualTo("jpg");
            assertThat(processed.content()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
        }

        @Test
        void transparentImageStaysPng() {
            ProcessedImage processed = processor.fit(processor.decode(image("png", 20, 20, true)), 640);

            assertThat(processed.contentType()).isEqualTo("image/png");
            assertThat(processed.extension()).isEqualTo("png");
            assertThat(pixel(processed.content(), 19, 19).getAlpha()).isZero();
        }

        @Test
        void webpIsReencodedAsJpegWithSameColors() {
            ProcessedImage processed = processor.fit(processor.decode(resource("/images/red-blue.webp")), 640);
            int width = processed.width();
            int height = processed.height();

            assertThat(processed.contentType()).isEqualTo("image/jpeg");
            assertThat(isRed(pixel(processed.content(), width / 10, height / 2))).isTrue();
            assertThat(isBlue(pixel(processed.content(), width * 9 / 10, height / 2))).isTrue();
        }

        @Test
        void returnedContentCannotBeChangedFromOutside() {
            ProcessedImage processed = processor.fit(processor.decode(image("png", 10, 10)), 640);
            byte[] first = processed.content();

            first[0] = 0;

            assertThat(processed.content()[0]).isEqualTo((byte) 0xFF);
        }
    }

    @Nested
    class Exif {

        @Test
        void stripsExifFromOutput() {
            byte[] jpeg = withOrientation(image("jpeg", 100, 100), 1);
            assertThat(latin1(jpeg)).contains("Exif");

            DecodedImage decoded = processor.decode(jpeg);

            assertThat(latin1(processor.fit(decoded, 2048).content())).doesNotContain("Exif");
            assertThat(latin1(processor.squareThumbnail(jpeg, 400).content())).doesNotContain("Exif");
        }

        // 元の画像は 200×100 で、左半分が赤・右半分が青
        @ParameterizedTest(name = "Orientation {0}: 上が {1}・下が {2}・幅 {3}")
        @CsvSource({
            "1, none, none, 200",
            "3, none, none, 200",
            "6, red, blue, 100",
            "8, blue, red, 100",
        })
        void appliesOrientation(int orientation, String top, String bottom, int width) {
            byte[] jpeg = withOrientation(image("jpeg", 200, 100), orientation);

            ProcessedImage processed = processor.fit(processor.decode(jpeg), 2048);
            byte[] out = processed.content();

            assertThat(processed.width()).isEqualTo(width);
            if (width == 100) {
                // 90 度回したので縦長になり、左右の色が上下に移る
                assertThat(processed.height()).isEqualTo(200);
                assertThat(color(pixel(out, 50, 20))).isEqualTo(top);
                assertThat(color(pixel(out, 50, 180))).isEqualTo(bottom);
            } else if (orientation == 3) {
                // 180 度回したので左右の色が入れ替わる
                assertThat(isBlue(pixel(out, 20, 50))).isTrue();
                assertThat(isRed(pixel(out, 180, 50))).isTrue();
            } else {
                assertThat(isRed(pixel(out, 20, 50))).isTrue();
                assertThat(isBlue(pixel(out, 180, 50))).isTrue();
            }
        }

        private static String color(Color c) {
            return isRed(c) ? "red" : isBlue(c) ? "blue" : "other";
        }
    }

    @Nested
    class Gif {

        @Test
        void originalGifKeepsBytesAndAnimation() {
            byte[] gif = animatedGif(3);

            DecodedImage decoded = processor.decode(gif);
            ProcessedImage original = processor.originalGif(decoded);

            assertThat(decoded.isGif()).isTrue();
            assertThat(original.content()).isEqualTo(gif);
            assertThat(original.contentType()).isEqualTo("image/gif");
            assertThat(original.extension()).isEqualTo("gif");
            assertThat(original.width()).isEqualTo(40);
            assertThat(original.height()).isEqualTo(30);
            assertThat(frames(original.content())).isEqualTo(3);
        }

        @Test
        void thumbnailOfGifIsStillImage() {
            ProcessedImage thumbnail = processor.fit(processor.decode(animatedGif(3)), 640);

            assertThat(thumbnail.contentType()).isEqualTo("image/jpeg");
            // 1 コマ目（赤）から作る
            assertThat(isRed(pixel(thumbnail.content(), 20, 15))).isTrue();
        }

        @Test
        void originalGifRejectsOtherFormats() {
            DecodedImage png = processor.decode(image("png", 10, 10));

            assertThatThrownBy(() -> processor.originalGif(png)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
