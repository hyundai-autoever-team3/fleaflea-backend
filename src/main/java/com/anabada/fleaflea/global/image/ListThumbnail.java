package com.anabada.fleaflea.global.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** 원본 파일과 DB 키를 유지하면서 목록용 썸네일을 생성한다. */
public final class ListThumbnail {

    private static final int MAX_EDGE = 480;
    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int SUBSAMPLING_EDGE = MAX_EDGE * 2;
    private static final float JPEG_QUALITY = 0.8f;
    private static final String THUMBNAIL_PREFIX = "thumbnails/v1/";
    private static final String THUMBNAIL_EXTENSION = ".jpg";
    private static final int DEFAULT_ORIENTATION = 1;

    private ListThumbnail() {
    }

    public static boolean supports(String imageKey) {
        return imageKey != null
                && (imageKey.startsWith("items/") || imageKey.startsWith("collection-items/"));
    }

    public static String createThumbnailKey(String originalImageKey) {
        return THUMBNAIL_PREFIX + originalImageKey + THUMBNAIL_EXTENSION;
    }

    public static byte[] render(byte[] originalBytes) throws IOException {
        BufferedImage decodedImage = readImage(originalBytes);
        BufferedImage scaledImage = resizeImage(decodedImage);
        int orientation = readOrientation(originalBytes);
        BufferedImage orientedImage = applyOrientation(scaledImage, orientation);
        return encodeJpeg(orientedImage);
    }

    private static BufferedImage readImage(byte[] originalBytes) throws IOException {
        if (originalBytes.length > MAX_FILE_SIZE) {
            throw new IOException("Image exceeds byte limit");
        }
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(originalBytes)
        )) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image exceeds pixel limit");
                }
                ImageReadParam readParam = reader.getDefaultReadParam();
                int sampling = Math.max(1, Math.max(width, height) / SUBSAMPLING_EDGE);
                readParam.setSourceSubsampling(sampling, sampling, 0, 0);
                return reader.read(0, readParam);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage resizeImage(BufferedImage originalImage) {
        double ratio = Math.min(1.0,
                (double) MAX_EDGE / Math.max(originalImage.getWidth(), originalImage.getHeight()));
        int width = Math.max(1, (int) Math.round(originalImage.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(originalImage.getHeight() * ratio));
        BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaledImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(originalImage, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaledImage;
    }

    private static BufferedImage applyOrientation(
            BufferedImage scaledImage,
            int orientation
    ) {
        int width = scaledImage.getWidth();
        int height = scaledImage.getHeight();
        boolean swapDimensions = orientation >= 5 && orientation <= 8;
        BufferedImage orientedImage = new BufferedImage(
                swapDimensions ? height : width,
                swapDimensions ? width : height,
                BufferedImage.TYPE_INT_RGB
        );
        for (int sourceY = 0; sourceY < height; sourceY++) {
            for (int sourceX = 0; sourceX < width; sourceX++) {
                int targetX = sourceX;
                int targetY = sourceY;
                switch (orientation) {
                    case 2 -> targetX = width - 1 - sourceX;
                    case 3 -> {
                        targetX = width - 1 - sourceX;
                        targetY = height - 1 - sourceY;
                    }
                    case 4 -> targetY = height - 1 - sourceY;
                    case 5 -> {
                        targetX = sourceY;
                        targetY = sourceX;
                    }
                    case 6 -> {
                        targetX = height - 1 - sourceY;
                        targetY = sourceX;
                    }
                    case 7 -> {
                        targetX = height - 1 - sourceY;
                        targetY = width - 1 - sourceX;
                    }
                    case 8 -> {
                        targetX = sourceY;
                        targetY = width - 1 - sourceX;
                    }
                    default -> {
                    }
                }
                orientedImage.setRGB(targetX, targetY, scaledImage.getRGB(sourceX, sourceY));
            }
        }
        return orientedImage;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(imageBytes)) {
            writer.setOutput(output);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        return imageBytes.toByteArray();
    }

    private static int readOrientation(byte[] imageBytes) throws IOException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = directory == null ? null : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation == null ? DEFAULT_ORIENTATION : orientation;
        } catch (ImageProcessingException exception) {
            throw new IOException("Cannot inspect image orientation", exception);
        }
    }
}
