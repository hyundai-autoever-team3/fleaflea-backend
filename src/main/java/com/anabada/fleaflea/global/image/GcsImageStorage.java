package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "gcs")
public class GcsImageStorage implements ImageStorage {

    private static final int MAXIMUM_CACHED_URLS = 10_000;
    private final Storage storage;
    private final String bucket;
    private final Duration signedUrlDuration;
    private final Ticker ticker;
    private final long cacheDurationNanos;
    private final Cache<String, CachedSignedUrl> signedUrlCache;

    @Autowired
    public GcsImageStorage(
            Storage storage,
            @Value("${gcp.storage.bucket}") String bucket,
            @Value("${gcp.storage.signed-url-duration:1h}") Duration signedUrlDuration
    ) {
        this(storage, bucket, signedUrlDuration, Ticker.systemTicker());
    }

    GcsImageStorage(Storage storage, String bucket, Duration signedUrlDuration, Ticker ticker) {
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
        this.ticker = ticker;
        long durationSeconds = signedUrlDuration.getSeconds();
        long renewalMarginSeconds = Math.max(1, Math.min(60, durationSeconds / 10));
        this.cacheDurationNanos = TimeUnit.SECONDS.toNanos(durationSeconds - renewalMarginSeconds);
        this.signedUrlCache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHED_URLS)
                .ticker(ticker)
                .expireAfter(new Expiry<String, CachedSignedUrl>() {
                    @Override
                    public long expireAfterCreate(String key, CachedSignedUrl value, long currentTime) {
                        return Math.max(0, value.expiresAtNanos() - currentTime);
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedSignedUrl value,
                                                  long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, CachedSignedUrl value,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public void upload(String imageKey, byte[] imageBytes, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, imageKey)
                .setContentType(contentType)
                .build();

        try {
            storage.create(blobInfo, imageBytes);
            signedUrlCache.invalidate(imageKey);
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public void delete(String imageKey) {
        try {
            storage.delete(BlobId.of(bucket, imageKey));
            signedUrlCache.invalidate(imageKey);
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
            signedUrlCache.invalidate(targetKey);
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public String getUrl(String imageKey) {
        if (cacheDurationNanos == 0) {
            return signUrl(imageKey);
        }
        return signedUrlCache.get(imageKey, key -> {
            // Count signing latency toward validity, including V4's second-level timestamp precision.
            long expiresAtNanos = ticker.read() + cacheDurationNanos;
            return new CachedSignedUrl(signUrl(key), expiresAtNanos);
        }).url();
    }

    private String signUrl(String imageKey) {
        return storage.signUrl(
                BlobInfo.newBuilder(bucket, imageKey).build(),
                signedUrlDuration.getSeconds(),
                TimeUnit.SECONDS,
                Storage.SignUrlOption.withV4Signature()
        ).toExternalForm();
    }

    private record CachedSignedUrl(String url, long expiresAtNanos) {
    }
}
