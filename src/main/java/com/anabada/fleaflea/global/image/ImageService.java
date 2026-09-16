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

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class ImageService {

    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;

    private static final Pattern IMAGE_KEY_PATTERN = Pattern.compile(
            "^(profiles|collection-items|items|markets)/"
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

    /**
     * 새 이미지를 업로드하고 교체 전후 키를 반환함.
     * 기존 이미지는 삭제하지 않는다!
     */
    public ImageReplacement prepareReplacement(
            String previousKey,
            MultipartFile file,
            ImageCategory category
    ) {
        if (category == null) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_CATEGORY);
        }

        if (previousKey == null
                || !IMAGE_KEY_PATTERN.matcher(previousKey).matches()
                || !previousKey.startsWith(category.getPrefix() + "/")) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_KEY);
        }

        String newKey = upload(file, category);

        return new ImageReplacement(previousKey, newKey);
    }

    /**
     * 호출한 서비스의 DB 트랜잭션 결과에 따라 이미지를 정리!
     * 반환된 새 이미지 키를 같은 트랜잭션에서 DB에 저장해야 함!!
     */
    public String replace(
            String previousKey,
            MultipartFile file,
            ImageCategory category
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException(
                    "이미지 교체는 쓰기 가능한 DB 트랜잭션 안에서 호출해야 합니다."
            );
        }

        ImageReplacement replacement =
                prepareReplacement(previousKey, file, category);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        String keyToDelete;

                        if (status == STATUS_COMMITTED) {
                            keyToDelete = replacement.previousKey();
                        } else if (status == STATUS_ROLLED_BACK) {
                            keyToDelete = replacement.newKey();
                        } else {
                            log.warn(
                                    "트랜잭션 결과 불명확: 이미지 정리 보류. previousKey={}, newKey={}",
                                    replacement.previousKey(),
                                    replacement.newKey()
                            );
                            return;
                        }

                        try {
                            delete(keyToDelete);
                        } catch (RuntimeException e) {
                            log.error(
                                    "이미지 정리 실패: 재처리 필요. imageKey={}",
                                    keyToDelete,
                                    e
                            );
                        }
                    }
                }
        );

        return replacement.newKey();
    }

    public String getUrl(String imageKey) {
        if (imageKey == null) {
            return null;
        }
        return s3Client.utilities()
                .getUrl(builder -> builder
                        .bucket(bucket)
                        .key(imageKey))
                .toExternalForm();
    }
}
