package com.anabada.fleaflea.global.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Versioned derivatives; originals and their database keys are never replaced. */
public final class ListThumbnail {
    public static final int MAX_EDGE = 480;
    private ListThumbnail() {}

    public static boolean supports(String key) {
        return key != null && (key.startsWith("items/") || key.startsWith("collection-items/"));
    }

    public static String key(String original) {
        return "thumbnails/v1/" + original + ".jpg";
    }

    public static byte[] render(byte[] original) throws IOException {
        if (original.length > 100 * 1024 * 1024) throw new IOException("Image exceeds byte limit");
        BufferedImage decoded;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000)
                    throw new IOException("Image exceeds pixel limit");
                var param = reader.getDefaultReadParam();
                int sampling = Math.max(1, Math.max(width, height) / (MAX_EDGE * 2));
                param.setSourceSubsampling(sampling, sampling, 0, 0);
                decoded = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
        double ratio = Math.min(1.0, (double) MAX_EDGE / Math.max(decoded.getWidth(), decoded.getHeight()));
        int w = Math.max(1, (int) Math.round(decoded.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(decoded.getHeight() * ratio));
        var scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, w, h);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(decoded, 0, 0, w, h, null);
        } finally { graphics.dispose(); }
        int orientation = orientation(original);
        boolean swap = orientation >= 5 && orientation <= 8;
        var oriented = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int dx = x, dy = y;
            switch (orientation) {
                case 2 -> dx = w - 1 - x;
                case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                case 4 -> dy = h - 1 - y;
                case 5 -> { dx = y; dy = x; }
                case 6 -> { dx = h - 1 - y; dy = x; }
                case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                case 8 -> { dx = y; dy = w - 1 - x; }
                default -> { }
            }
            oriented.setRGB(dx, dy, scaled.getRGB(x, y));
        }
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var bytes = new ByteArrayOutputStream();
        try (var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            var params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.8f);
            writer.write(null, new javax.imageio.IIOImage(oriented, null, null), params);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    private static int orientation(byte[] bytes) throws IOException {
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            var directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer value = directory == null ? null : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return value == null ? 1 : value;
        } catch (com.drew.imaging.ImageProcessingException exception) {
            throw new IOException("Cannot inspect image orientation", exception);
        }
    }
}
