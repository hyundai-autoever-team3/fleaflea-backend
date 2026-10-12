package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "s3", matchIfMissing = true)
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3ImageStorage(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public void upload(String imageKey, byte[] imageBytes, String contentType) {
        try {
            s3Client.putObject(
                    request -> request.bucket(bucket).key(imageKey).contentType(contentType),
                    RequestBody.fromBytes(imageBytes)
            );
        } catch (SdkException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public void delete(String imageKey) {
        try {
            s3Client.deleteObject(request -> request.bucket(bucket).key(imageKey));
        } catch (SdkException exception) {
            throw new ImageException(ErrorCode.IMAGE_DELETE_FAILED, exception);
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        try {
            s3Client.copyObject(request -> request
                    .sourceBucket(bucket).sourceKey(sourceKey)
                    .destinationBucket(bucket).destinationKey(targetKey));
        } catch (SdkException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public String getUrl(String imageKey) {
        return s3Client.utilities().getUrl(request -> request.bucket(bucket).key(imageKey))
                .toExternalForm();
    }
}
