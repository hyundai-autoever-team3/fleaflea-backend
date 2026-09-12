package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ImageService {

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;

    private static final Pattern IMAGE_KEY_PATTERN = Pattern.compile(
            "^(profiles|collection-items|items)/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$"
    );

    private final S3Client s3Client;
    private final String bucket;

    public ImageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String upload(MultipartFile file, ImageCategory category) {
        if (category == null) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_CATEGORY);
        }

        if (file == null || file.isEmpty()) {
            throw new ImageException(ErrorCode.INVALID_IMAGE);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageException(ErrorCode.IMAGE_TOO_LARGE);
        }

        byte[] bytes = readBytes(file);
        String extension = detectImageExtension(bytes);
        String contentType = extension.equals("jpg")
                ? "image/jpeg"
                : "image/png";

        String imageKey = category.getPrefix()
                + "/" + UUID.randomUUID() + "." + extension;

        try {
            s3Client.putObject(
                    request -> request.bucket(bucket)
                            .key(imageKey)
                            .contentType(contentType),
                    RequestBody.fromBytes(bytes)
            );

            return imageKey;
        } catch (SdkException e) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, e);
        }
    }

    public void delete(String imageKey) {
        if (imageKey == null
                || !IMAGE_KEY_PATTERN.matcher(imageKey).matches()) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_KEY);
        }

        try {
            s3Client.deleteObject(
                    request -> request.bucket(bucket).key(imageKey)
            );
        } catch (SdkException e) {
            throw new ImageException(ErrorCode.IMAGE_DELETE_FAILED, e);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_FILE_SIZE + 1);

            if (bytes.length > MAX_FILE_SIZE) {
                throw new ImageException(ErrorCode.IMAGE_TOO_LARGE);
            }

            return bytes;
        } catch (IOException e) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, e);
        }
    }

    private String detectImageExtension(byte[] bytes) {
        try (MemoryCacheImageInputStream input =
                     new MemoryCacheImageInputStream(
                             new ByteArrayInputStream(bytes))) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new ImageException(ErrorCode.INVALID_IMAGE);
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input);

                String format = reader.getFormatName()
                        .toLowerCase(Locale.ROOT);

                String extension = switch (format) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    default -> throw new ImageException(
                            ErrorCode.INVALID_IMAGE);
                };

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new ImageException(ErrorCode.INVALID_IMAGE);
                }

                if (reader.read(0) == null) {
                    throw new ImageException(ErrorCode.INVALID_IMAGE);
                }

                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageException(ErrorCode.INVALID_IMAGE, e);
        }
    }
}