package com.timelinesns.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * テスト用の画像を作る・読む。画像処理（ImageProcessor）と、画像を扱う API のテストで使う。
 */
public final class TestImages {

    private TestImages() {
    }

    /**
     * 左半分が赤・右半分が青の画像。
     */
    public static byte[] image(String format, int width, int height) {
        return image(format, width, height, false);
    }

    /**
     * 左半分が赤・右半分が青の画像。{@code transparent} なら右下の 1 画素を透明にする。
     */
    public static byte[] image(String format, int width, int height, boolean transparent) {
        return encode(redBlue(width, height, transparent), format);
    }

    /**
     * 左・中央・右の 3 等分を赤・緑・青に塗った画像（中央の切り抜きを確かめる）。
     */
    public static byte[] threeColumns(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            int third = width / 3;
            g.setColor(Color.RED);
            g.fillRect(0, 0, third, height);
            g.setColor(Color.GREEN);
            g.fillRect(third, 0, width - 2 * third, height);
            g.setColor(Color.BLUE);
            g.fillRect(width - third, 0, third, height);
        } finally {
            g.dispose();
        }
        return encode(image, format);
    }

    /**
     * コマ数 {@code frames}（1〜3）の GIF アニメーション（コマごとに色を変える）。
     */
    public static byte[] animatedGif(int frames) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /**
     * GIF のコマ数。
     */
    public static int frames(byte[] gif) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            ImageReader reader = ImageIO.getImageReaders(in).next();
            try {
                reader.setInput(in);
                return reader.getNumImages(true);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * JPEG の先頭（SOI の直後）に、Orientation だけを持つ Exif（APP1）を差し込む。
     */
    public static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,                // APP1、長さ 34 バイト（長さ自身を含む）
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,        // TIFF ヘッダ（ビッグエンディアン、IFD0 は 8 バイト目）
            0x00, 0x01,                                          // IFD0 の項目数 1
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,      // Orientation（0x0112）、SHORT、1 個
            0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,                              // 次の IFD なし
        };
        byte[] out = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, out, 0, 2);
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            return Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * テスト用のリソース（src/test/resources）を読む。例: {@code /images/red-blue.webp}（左半分が赤・右半分が青）
     */
    public static byte[] resource(String path) {
        try (InputStream in = TestImages.class.getResourceAsStream(path)) {
            return Objects.requireNonNull(in, path).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage redBlue(int width, int height, boolean transparent) {
        int type = transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, width / 2, height);
            g.setColor(Color.BLUE);
            g.fillRect(width / 2, 0, width - width / 2, height);
        } finally {
            g.dispose();
        }
        if (transparent) {
            image.setRGB(width - 1, height - 1, 0x00000000);
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalArgumentException("書き出せない形式です: " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
