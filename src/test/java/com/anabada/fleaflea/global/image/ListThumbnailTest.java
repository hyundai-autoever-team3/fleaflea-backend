package com.anabada.fleaflea.global.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ListThumbnailTest {
    static byte[] image(int width, int height, boolean alpha) throws Exception {
        var image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var random = new Random(194);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            image.setRGB(x, y, alpha ? 0 : random.nextInt(0x1000000));
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, alpha ? "png" : "jpg", out);
        return out.toByteArray();
    }

    @Test void largePhotoIsSmallerAndKeepsAspectRatio() throws Exception {
        byte[] original = image(2400, 1200, false);
        byte[] thumbnail = ListThumbnail.render(original);
        var decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(decoded.getWidth()).isEqualTo(480);
        assertThat(decoded.getHeight()).isEqualTo(240);
        assertThat(thumbnail.length).isLessThan(original.length / 5);
    }

    @Test void smallTransparentImageIsNotUpscaledAndHasWhiteBackground() throws Exception {
        var decoded = ImageIO.read(new ByteArrayInputStream(ListThumbnail.render(image(60, 30, true))));
        assertThat(decoded.getWidth()).isEqualTo(60);
        assertThat(decoded.getHeight()).isEqualTo(30);
        assertThat(new Color(decoded.getRGB(20, 10))).isEqualTo(Color.WHITE);
    }

    @Test void exifPortraitOrientationIsApplied() throws Exception {
        byte[] jpeg = image(80, 40, false);
        var out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(java.util.HexFormat.of().parseHex(
                "ffe100224578696600004d4d002a00000008000101120003000000010006000000000000"));
        out.write(jpeg, 2, jpeg.length - 2);
        var decoded = ImageIO.read(new ByteArrayInputStream(ListThumbnail.render(out.toByteArray())));
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(80);
    }

    @Test void invalidInputIsRejected() {
        assertThatThrownBy(() -> ListThumbnail.render(new byte[]{1, 2, 3})).isInstanceOf(java.io.IOException.class);
    }
}
