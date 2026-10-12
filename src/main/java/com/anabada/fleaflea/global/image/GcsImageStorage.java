package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "gcs")
public class GcsImageStorage implements ImageStorage {

    private final Storage storage;
    private final String bucket;
    private final Duration signedUrlDuration;

    public GcsImageStorage(
            Storage storage,
            @Value("${gcp.storage.bucket}") String bucket,
            @Value("${gcp.storage.signed-url-duration:1h}") Duration signedUrlDuration
    ) {
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("GCS bucket is required");
        }
        if (signedUrlDuration.getSeconds() < 1
                || signedUrlDuration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("GCS signed URL duration must be between 1s and 7d");
        }

        this.storage = storage;
        this.bucket = bucket;
        this.signedUrlDuration = signedUrlDuration;
    }

    @Override
    public void upload(String imageKey, byte[] imageBytes, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, imageKey)
                .setContentType(contentType)
                .build();

        try {
            storage.create(blobInfo, imageBytes);
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public void delete(String imageKey) {
        try {
            storage.delete(BlobId.of(bucket, imageKey));
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_DELETE_FAILED, exception);
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        Storage.CopyRequest copyRequest = Storage.CopyRequest.newBuilder()
                .setSource(BlobId.of(bucket, sourceKey))
                .setTarget(BlobId.of(bucket, targetKey))
                .build();

        try {
            storage.copy(copyRequest).getResult();
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public String getUrl(String imageKey) {
        return storage.signUrl(
                BlobInfo.newBuilder(bucket, imageKey).build(),
                signedUrlDuration.getSeconds(),
                TimeUnit.SECONDS,
                Storage.SignUrlOption.withV4Signature()
        ).toExternalForm();
    }
}
