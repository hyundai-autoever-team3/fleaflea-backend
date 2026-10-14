package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "gcs")
public class GcsImageStorage implements ImageStorage {

    private static final int MAXIMUM_CACHED_URLS = 10_000;
    private static final int MAXIMUM_CACHED_THUMBNAILS = 10_000;
    private static final Duration THUMBNAIL_CACHE_DURATION = Duration.ofMinutes(1);
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    private static final String THUMBNAIL_CACHE_CONTROL = "private, max-age=3600";

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
        this.thumbnailExists = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHED_THUMBNAILS)
                .ticker(ticker)
                .expireAfterWrite(THUMBNAIL_CACHE_DURATION)
                .build();
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
    public void upload(
            String imageKey,
            byte[] imageBytes,
            String contentType
    ) {
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
            signedUrlCache.invalidate(ListThumbnail.createThumbnailKey(imageKey));
            if (ListThumbnail.supports(imageKey)) {
                try {
                    storage.delete(BlobId.of(bucket, ListThumbnail.createThumbnailKey(imageKey)));
                } catch (StorageException cleanupFailure) {
                    log.warn("썸네일 삭제 실패: imageKey={}", imageKey, cleanupFailure);
                }
            }
        } catch (StorageException exception) {
            throw new ImageException(ErrorCode.IMAGE_DELETE_FAILED, exception);
        }
    }

    @Override
    public void copy(
            String sourceKey,
            String targetKey
    ) {
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

    private void createThumbnail(
            String imageKey,
            byte[] imageBytes
    ) {
        if (!ListThumbnail.supports(imageKey)) {
            return;
        }
        thumbnailExists.invalidate(imageKey);
        signedUrlCache.invalidate(ListThumbnail.createThumbnailKey(imageKey));
        try {
            byte[] thumbnail = ListThumbnail.render(imageBytes);
            if (thumbnail.length >= imageBytes.length) {
                return;
            }
            storage.create(BlobInfo.newBuilder(bucket, ListThumbnail.createThumbnailKey(imageKey))
                    .setContentType(THUMBNAIL_CONTENT_TYPE)
                    .setCacheControl(THUMBNAIL_CACHE_CONTROL)
                    .build(), thumbnail);
            thumbnailExists.put(imageKey, true);
        } catch (IOException | RuntimeException failure) {
            // 썸네일 생성 실패는 원본 업로드와 트랜잭션 정리를 실패시키지 않는다.
            log.warn("썸네일 생성 실패, 원본 사용: imageKey={}", imageKey, failure);
        }
    }

    private void copyThumbnail(
            String sourceImageKey,
            String targetImageKey
    ) {
        if (!ListThumbnail.supports(targetImageKey)) {
            return;
        }
        thumbnailExists.invalidate(targetImageKey);
        signedUrlCache.invalidate(ListThumbnail.createThumbnailKey(targetImageKey));
        try {
            if (storage.get(BlobId.of(bucket, ListThumbnail.createThumbnailKey(sourceImageKey))) == null) {
                return;
            }
            storage.copy(Storage.CopyRequest.newBuilder()
                    .setSource(BlobId.of(bucket, ListThumbnail.createThumbnailKey(sourceImageKey)))
                    .setTarget(BlobId.of(bucket, ListThumbnail.createThumbnailKey(targetImageKey))).build()).getResult();
            thumbnailExists.put(targetImageKey, true);
        } catch (RuntimeException failure) {
            log.warn("썸네일 복사 실패, 원본 사용: imageKey={}", targetImageKey, failure);
        }
    }

    @Override
    public Map<String, String> getListUrls(Collection<String> imageKeys) {
        List<String> uniqueImageKeys = imageKeys.stream().filter(Objects::nonNull).distinct().toList();
        List<String> uncachedImageKeys = uniqueImageKeys.stream().filter(ListThumbnail::supports)
                .filter(imageKey -> thumbnailExists.getIfPresent(imageKey) == null).toList();
        if (!uncachedImageKeys.isEmpty()) {
            try {
                // 페이지 내 썸네일 존재 여부를 SDK 배치 조회로 확인한다.
                List<Blob> thumbnailBlobs = storage.get(uncachedImageKeys.stream()
                        .map(imageKey -> BlobId.of(bucket, ListThumbnail.createThumbnailKey(imageKey))).toList());
                for (int index = 0; index < uncachedImageKeys.size(); index++) {
                    thumbnailExists.put(uncachedImageKeys.get(index), thumbnailBlobs.get(index) != null);
                }
            } catch (RuntimeException failure) {
                // 조회 실패는 캐시하지 않고 다음 요청에서 재시도한다.
                log.warn("썸네일 존재 확인 실패, 원본 사용", failure);
            }
        }
        Map<String, String> imageUrls = new HashMap<>();
        for (String imageKey : uniqueImageKeys) {
            boolean exists = ListThumbnail.supports(imageKey) && Boolean.TRUE.equals(thumbnailExists.getIfPresent(imageKey));
            imageUrls.put(imageKey, getUrl(exists ? ListThumbnail.createThumbnailKey(imageKey) : imageKey));
        }
        return imageUrls;
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

    private record CachedSignedUrl(
            String url,
            long expiresAtNanos
    ) {
    }
}
