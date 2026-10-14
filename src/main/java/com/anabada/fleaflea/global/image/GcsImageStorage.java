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

@lombok.extern.slf4j.Slf4j
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
    private final Cache<String, Boolean> thumbnailExists;

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
        this.thumbnailExists = Caffeine.newBuilder().maximumSize(10_000)
                .ticker(ticker).expireAfterWrite(Duration.ofMinutes(1)).build();
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
            createThumbnail(imageKey, imageBytes);
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    @Override
    public void delete(String imageKey) {
        try {
            storage.delete(BlobId.of(bucket, imageKey));
            signedUrlCache.invalidate(imageKey);
            thumbnailExists.invalidate(imageKey);
            signedUrlCache.invalidate(ListThumbnail.key(imageKey));
            if (ListThumbnail.supports(imageKey)) {
                try { storage.delete(BlobId.of(bucket, ListThumbnail.key(imageKey))); }
                catch (StorageException cleanupFailure) {
                    log.warn("썸네일 삭제 실패: imageKey={}", imageKey, cleanupFailure);
                }
            }
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
            copyThumbnail(sourceKey, targetKey);
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    private void createThumbnail(String key, byte[] bytes) {
        if (!ListThumbnail.supports(key)) return;
        thumbnailExists.invalidate(key);
        signedUrlCache.invalidate(ListThumbnail.key(key));
        try {
            byte[] thumbnail = ListThumbnail.render(bytes);
            if (thumbnail.length >= bytes.length) return;
            storage.create(BlobInfo.newBuilder(bucket, ListThumbnail.key(key))
                    .setContentType("image/jpeg").setCacheControl("private, max-age=3600").build(), thumbnail);
            thumbnailExists.put(key, true);
        } catch (java.io.IOException | RuntimeException failure) {
            // A derivative is optional: original upload and transaction cleanup retain their contract.
            log.warn("썸네일 생성 실패, 원본 사용: imageKey={}", key, failure);
        }
    }

    private void copyThumbnail(String source, String target) {
        if (!ListThumbnail.supports(target)) return;
        thumbnailExists.invalidate(target);
        signedUrlCache.invalidate(ListThumbnail.key(target));
        try {
            if (storage.get(BlobId.of(bucket, ListThumbnail.key(source))) == null) return;
            storage.copy(Storage.CopyRequest.newBuilder()
                    .setSource(BlobId.of(bucket, ListThumbnail.key(source)))
                    .setTarget(BlobId.of(bucket, ListThumbnail.key(target))).build()).getResult();
            thumbnailExists.put(target, true);
        } catch (RuntimeException failure) {
            log.warn("썸네일 복사 실패, 원본 사용: imageKey={}", target, failure);
        }
    }

    @Override
    public java.util.Map<String, String> getListUrls(java.util.Collection<String> keys) {
        var unique = keys.stream().filter(java.util.Objects::nonNull).distinct().toList();
        var missing = unique.stream().filter(ListThumbnail::supports)
                .filter(key -> thumbnailExists.getIfPresent(key) == null).toList();
        if (!missing.isEmpty()) {
            try {
                // One SDK batch for a page, rather than one metadata round-trip per item.
                var blobs = storage.get(missing.stream()
                        .map(key -> BlobId.of(bucket, ListThumbnail.key(key))).toList());
                for (int i = 0; i < missing.size(); i++) thumbnailExists.put(missing.get(i), blobs.get(i) != null);
            } catch (RuntimeException failure) {
                // Keep failures uncached so recovery is visible on the next request.
                log.warn("썸네일 존재 확인 실패, 원본 사용", failure);
            }
        }
        java.util.Map<String, String> urls = new java.util.HashMap<>();
        for (String key : unique) {
            boolean exists = ListThumbnail.supports(key) && Boolean.TRUE.equals(thumbnailExists.getIfPresent(key));
            urls.put(key, getUrl(exists ? ListThumbnail.key(key) : key));
        }
        return urls;
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
