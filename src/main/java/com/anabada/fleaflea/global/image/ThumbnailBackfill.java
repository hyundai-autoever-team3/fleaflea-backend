package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.util.List;

/** Spring 실행과 분리된 기존 이미지 썸네일 생성 명령이다. */
public final class ThumbnailBackfill {

    private static final int DEFAULT_CANDIDATE_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int NOT_FOUND_STATUS = 404;
    private static final int PRECONDITION_FAILED_STATUS = 412;
    private static final String BUCKET_ARGUMENT = "--bucket=";
    private static final String LIMIT_ARGUMENT = "--limit=";
    private static final List<String> IMAGE_PREFIXES = List.of("items/", "collection-items/");

    private ThumbnailBackfill() {
    }

    public static void main(String[] args) throws Exception {
        String bucket = null;
        int candidateLimit = DEFAULT_CANDIDATE_LIMIT;
        boolean apply = false;
        for (String argument : args) {
            if (argument.startsWith(BUCKET_ARGUMENT)) {
                bucket = argument.substring(BUCKET_ARGUMENT.length());
            } else if (argument.startsWith(LIMIT_ARGUMENT)) {
                candidateLimit = Integer.parseInt(argument.substring(LIMIT_ARGUMENT.length()));
            } else if (argument.equals("--apply")) {
                apply = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + argument);
            }
        }
        if (bucket == null || bucket.isBlank() || candidateLimit < 1) {
            throw new IllegalArgumentException("Required: --bucket=BUCKET [--limit=100] [--apply]");
        }
        Summary summary = run(StorageOptions.getDefaultInstance().getService(), bucket, candidateLimit, apply);
        printSummary(summary, apply);
        if (summary.failed() > 0) {
            throw new IllegalStateException("Backfill has failed objects; inspect summary before retrying");
        }
    }

    private static void printSummary(
            Summary summary,
            boolean apply
    ) {
        System.out.printf(
                "mode=%s scanned=%d candidates=%d created=%d skipped=%d failed=%d originalBytes=%d thumbnailBytes=%d%n",
                apply ? "apply" : "dry-run", summary.scanned(), summary.candidates(), summary.created(),
                summary.skipped(), summary.failed(), summary.originalBytes(), summary.thumbnailBytes()
        );
    }

    static Summary run(
            Storage storage,
            String bucket,
            int candidateLimit,
            boolean apply
    ) {
        int scanned = 0;
        int candidates = 0;
        int created = 0;
        int skipped = 0;
        int failed = 0;
        long originalBytes = 0;
        long thumbnailBytes = 0;
        for (String prefix : IMAGE_PREFIXES) {
            for (Blob originalBlob : storage.list(
                    bucket,
                    Storage.BlobListOption.prefix(prefix),
                    Storage.BlobListOption.pageSize(Math.min(candidateLimit, MAX_PAGE_SIZE))
            ).iterateAll()) {
                if (candidates >= candidateLimit) {
                    return new Summary(scanned, candidates, created, skipped, failed, originalBytes, thumbnailBytes);
                }
                scanned++;
                if (originalBlob.getName().endsWith("/") || originalBlob.getSize() > MAX_FILE_SIZE) {
                    skipped++;
                    continue;
                }
                String thumbnailKey = ListThumbnail.createThumbnailKey(originalBlob.getName());
                try {
                    if (storage.get(BlobId.of(bucket, thumbnailKey)) != null) {
                        skipped++;
                        continue;
                    }
                    candidates++;
                    if (!apply) {
                        continue;
                    }
                    byte[] sourceBytes = originalBlob.getContent(Blob.BlobSourceOption.generationMatch());
                    byte[] renderedBytes = ListThumbnail.render(sourceBytes);
                    if (renderedBytes.length >= sourceBytes.length) {
                        skipped++;
                        continue;
                    }
                    // 변환 중 원본이 변경되거나 삭제됐으면 파생 파일을 만들지 않는다.
                    Blob currentBlob = storage.get(originalBlob.getBlobId());
                    if (currentBlob == null || !originalBlob.getGeneration().equals(currentBlob.getGeneration())) {
                        skipped++;
                        continue;
                    }
                    BlobInfo thumbnailInfo = BlobInfo.newBuilder(bucket, thumbnailKey)
                            .setContentType("image/jpeg")
                            .setCacheControl("private, max-age=3600")
                            .build();
                    storage.create(thumbnailInfo, renderedBytes, Storage.BlobTargetOption.doesNotExist());
                    created++;
                    originalBytes += sourceBytes.length;
                    thumbnailBytes += renderedBytes.length;
                } catch (StorageException exception) {
                    if (exception.getCode() == PRECONDITION_FAILED_STATUS || exception.getCode() == NOT_FOUND_STATUS) {
                        skipped++;
                    } else {
                        failed++;
                        System.err.println("Failed: " + originalBlob.getName() + " code=" + exception.getCode());
                    }
                } catch (Exception exception) {
                    failed++;
                    System.err.println("Failed: " + originalBlob.getName() + " type=" + exception.getClass().getSimpleName());
                }
            }
        }
        return new Summary(scanned, candidates, created, skipped, failed, originalBytes, thumbnailBytes);
    }

    record Summary(
            int scanned,
            int candidates,
            int created,
            int skipped,
            int failed,
            long originalBytes,
            long thumbnailBytes
    ) {
    }
}
