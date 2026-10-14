package com.anabada.fleaflea.global.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListThumbnailTest {
    static byte[] createImageBytes(
            int width,
            int height,
            boolean alpha
    ) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Random random = new Random(194);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, alpha ? 0 : random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, alpha ? "png" : "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("큰 사진은 비율을 유지하면서 목록용 크기로 줄인다")
    void largePhotoIsSmallerAndKeepsAspectRatio() throws Exception {
        byte[] original = createImageBytes(2400, 1200, false);
        byte[] thumbnail = ListThumbnail.render(original);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(decoded.getWidth()).isEqualTo(480);
        assertThat(decoded.getHeight()).isEqualTo(240);
        assertThat(thumbnail.length).isLessThan(original.length / 5);
    }

    @Test
    @DisplayName("작은 투명 이미지는 확대하지 않고 흰 배경으로 변환한다")
    void smallTransparentImageIsNotUpscaledAndHasWhiteBackground() throws Exception {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(ListThumbnail.render(createImageBytes(60, 30, true))));
        assertThat(decoded.getWidth()).isEqualTo(60);
        assertThat(decoded.getHeight()).isEqualTo(30);
        assertThat(new Color(decoded.getRGB(20, 10))).isEqualTo(Color.WHITE);
    }

    @Test
    @DisplayName("EXIF 세로 방향을 썸네일에 반영한다")
    void exifPortraitOrientationIsApplied() throws Exception {
        byte[] jpeg = createImageBytes(80, 40, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(HexFormat.of().parseHex(
                "ffe100224578696600004d4d002a00000008000101120003000000010006000000000000"));
        out.write(jpeg, 2, jpeg.length - 2);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(ListThumbnail.render(out.toByteArray())));
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(80);
    }

    @Test
    @DisplayName("읽을 수 없는 이미지는 거부한다")
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> ListThumbnail.render(new byte[]{1, 2, 3})).isInstanceOf(IOException.class);
    }
}
