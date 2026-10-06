package com.timelinesns.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.timelinesns.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * アップロードされた画像を安全な形に作り直す（docs/03 の画像の方針）。
 * <ul>
 *   <li>拡張子や Content-Type は信用せず、ファイルの先頭バイトで形式を判定する（JPEG / PNG / WebP / GIF）</li>
 *   <li>Exif の向き（Orientation）を画素に反映してから、デコードした画素だけで再エンコードする。
 *       Exif（位置情報など）のメタデータは残らない</li>
 *   <li>透過があれば PNG、なければ JPEG で出力する。GIF は 1 フレーム目だけを使う</li>
 * </ul>
 */
@Component
public class ImageProcessor {

    /** 1 枚の上限（docs/02 F-11） */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    // 圧縮率の高い画像で、展開後にメモリを使い果たすのを防ぐ（約 4,000 万画素まで）
    private static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;

    public ImageProcessor() {
        // WebP の読み込み（TwelveMonkeys）を ImageIO に登録する
        ImageIO.scanForPlugins();
    }

    /**
     * 中央を正方形に切り抜き、{@code size} × {@code size} に縮小する（小さい画像は拡大しない）。
     */
    public ProcessedImage squareThumbnail(byte[] input, int size) {
        if (input.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "画像は 5MB 以内にしてください");
        }
        requireSupportedFormat(input);
        BufferedImage image = applyOrientation(decode(input), orientation(input));

        int side = Math.min(image.getWidth(), image.getHeight());
        BufferedImage square = image.getSubimage(
                (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
        return encode(resize(square, Math.min(size, side)));
    }

    private static void requireSupportedFormat(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return;
        }
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return;
        }
        if (startsWith(b, 0, 'G', 'I', 'F', '8') && b.length > 5 && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return;
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return;
        }
        throw unsupported();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    // 画素数を先に確かめてから展開する
    private static BufferedImage decode(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "画像の縦横のサイズが大きすぎます");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            // 壊れたファイル・形式を偽ったファイル
            throw unsupported();
        }
    }

    // Exif の Orientation（1〜8）。読めなければ 1（そのまま）
    private static int orientation(byte[] input) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | MetadataException | IOException | RuntimeException e) {
            // メタデータが読めないだけなら、向きの補正をしないで続ける
        }
        return 1;
    }

    private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        // 元の座標 (x, y) を表示上の座標へ移す行列。引数は (m00, m10, m01, m11, m02, m12) で、
        // x' = m00 * x + m01 * y + m02、y' = m10 * x + m11 * y + m12
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // 左右反転
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180 度回転
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // 上下反転
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // 左上と右下を結ぶ対角線で反転
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 時計回りに 90 度
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // 右上と左下を結ぶ対角線で反転
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: 反時計回りに 90 度
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // 大きく縮小するときは半分ずつ縮めて、ジャギーを抑える
    private static BufferedImage resize(BufferedImage image, int size) {
        BufferedImage current = image;
        int side = image.getWidth();
        do {
            side = Math.max(size, side / 2);
            BufferedImage next = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(current, 0, 0, side, side, null);
            } finally {
                g.dispose();
            }
            current = next;
        } while (side > size);
        return current;
    }

    private static ProcessedImage encode(BufferedImage image) {
        if (hasTransparency(image)) {
            return new ProcessedImage(write(image, "png", null), "image/png", "png");
        }
        // JPEG は透過を持てないので、白の背景に描いて RGB にする
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return new ProcessedImage(write(rgb, "jpeg", JPEG_QUALITY), "image/jpeg", "jpg");
    }

    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] write(BufferedImage image, String format, Float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (quality != null) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE",
                "JPEG・PNG・WebP・GIF の画像を選んでください");
    }

    /**
     * 作り直した画像。
     *
     * @param extension 保存するときの拡張子（jpg / png）
     */
    public record ProcessedImage(byte[] content, String contentType, String extension) {

        public ProcessedImage {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
