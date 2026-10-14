package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.time.Duration;
import java.util.List;

/** 원본 내용과 DB를 변경하지 않는 일회성 캐시 메타데이터 갱신 명령이다. */
public final class ImageCacheMetadataBackfill {

    private static final String BUCKET_ARGUMENT = "--bucket=";
    private static final String LIMIT_ARGUMENT = "--limit=";
    private static final String SIGNED_DURATION_ARGUMENT = "--signed-url-duration-seconds=";
    private static final String MAX_AGE_ARGUMENT = "--max-age-seconds=";
    private static final int DEFAULT_CANDIDATE_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int NOT_FOUND_STATUS = 404;
    private static final int PRECONDITION_FAILED_STATUS = 412;
    private static final List<String> IMAGE_PREFIXES = List.of("items/", "collection-items/");

    private ImageCacheMetadataBackfill() {
    }

    public static void main(String[] args) {
        String bucket = null;
        int candidateLimit = DEFAULT_CANDIDATE_LIMIT;
        long signedDurationSeconds = 0;
        long maxAgeSeconds = 300;
        boolean apply = false;
        for (String argument : args) {
            if (argument.startsWith(BUCKET_ARGUMENT)) {
                bucket = argument.substring(BUCKET_ARGUMENT.length());
            } else if (argument.startsWith(LIMIT_ARGUMENT)) {
                candidateLimit = Integer.parseInt(argument.substring(LIMIT_ARGUMENT.length()));
            } else if (argument.startsWith(SIGNED_DURATION_ARGUMENT)) {
                signedDurationSeconds = Long.parseLong(argument.substring(SIGNED_DURATION_ARGUMENT.length()));
            } else if (argument.startsWith(MAX_AGE_ARGUMENT)) {
                maxAgeSeconds = Long.parseLong(argument.substring(MAX_AGE_ARGUMENT.length()));
            } else if (argument.equals("--apply")) {
                apply = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + argument);
            }
        }
        if (bucket == null || bucket.isBlank() || candidateLimit < 1) {
            throw new IllegalArgumentException("Required: --bucket=BUCKET --signed-url-duration-seconds=SECONDS [--limit=100] [--max-age-seconds=300] [--apply]");
        }
        String cacheControl = GcsImageCachePolicy.createCacheControl(Duration.ofSeconds(signedDurationSeconds), maxAgeSeconds);
        Summary summary = run(StorageOptions.getDefaultInstance().getService(), bucket, candidateLimit, cacheControl, apply);
        System.out.printf("mode=%s candidates=%d updated=%d unchanged=%d raced=%d failed=%d%n",
                apply ? "apply" : "dry-run", summary.candidates(), summary.updated(), summary.unchanged(),
                summary.raced(), summary.failed());
        if (summary.failed() > 0) {
            throw new IllegalStateException("Metadata update has failed objects; inspect summary before retrying");
        }
    }

    static Summary run(
            Storage storage,
            String bucket,
            int candidateLimit,
            String cacheControl,
            boolean apply
    ) {
        int candidates = 0;
        int updated = 0;
        int unchanged = 0;
        int raced = 0;
        int failed = 0;
        for (String prefix : IMAGE_PREFIXES) {
            for (Blob originalBlob : storage.list(
                    bucket,
                    Storage.BlobListOption.prefix(prefix),
                    Storage.BlobListOption.pageSize(Math.min(candidateLimit, MAX_PAGE_SIZE))
            ).iterateAll()) {
                if (candidates >= candidateLimit) {
                    return new Summary(candidates, updated, unchanged, raced, failed);
                }
                if (!GcsImageCachePolicy.supports(originalBlob.getName())) {
                    continue;
                }
                if (cacheControl.equals(originalBlob.getCacheControl())) {
                    unchanged++;
                    continue;
                }
                candidates++;
                System.out.printf("object=%s oldCacheControl=%s newCacheControl=%s%n",
                        originalBlob.getName(), originalBlob.getCacheControl(), cacheControl);
                if (!apply) {
                    continue;
                }
                try {
                    // 조회한 세대의 Cache-Control만 갱신하고 나머지 메타데이터는 유지한다.
                    BlobInfo metadataPatch = BlobInfo.newBuilder(originalBlob.getBlobId())
                            .setCacheControl(cacheControl)
                            .build();
                    storage.update(metadataPatch,
                            Storage.BlobTargetOption.generationMatch(originalBlob.getGeneration()),
                            Storage.BlobTargetOption.metagenerationMatch(originalBlob.getMetageneration()));
                    updated++;
                } catch (StorageException exception) {
                    if (exception.getCode() == NOT_FOUND_STATUS || exception.getCode() == PRECONDITION_FAILED_STATUS) {
                        raced++;
                    } else {
                        failed++;
                        System.err.println("Failed: " + originalBlob.getName() + " code=" + exception.getCode());
                    }
                }
            }
        }
        return new Summary(candidates, updated, unchanged, raced, failed);
    }

    record Summary(
            int candidates,
            int updated,
            int unchanged,
            int raced,
            int failed
    ) {
    }
}
