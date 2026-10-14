package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

/** Explicit maintenance command, never started by Spring or during API requests. */
public final class ThumbnailBackfill {
    private ThumbnailBackfill() {}

    public static void main(String[] args) throws Exception {
        String bucket = null;
        int limit = 100;
        boolean apply = false;
        for (String arg : args) {
            if (arg.startsWith("--bucket=")) bucket = arg.substring(9);
            else if (arg.startsWith("--limit=")) limit = Integer.parseInt(arg.substring(8));
            else if (arg.equals("--apply")) apply = true;
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        if (bucket == null || bucket.isBlank() || limit < 1)
            throw new IllegalArgumentException("Required: --bucket=BUCKET [--limit=100] [--apply]");
        var result = run(StorageOptions.getDefaultInstance().getService(), bucket, limit, apply);
        System.out.printf("mode=%s scanned=%d candidates=%d created=%d skipped=%d failed=%d originalBytes=%d thumbnailBytes=%d%n",
                apply ? "apply" : "dry-run", result.scanned(), result.candidates(), result.created(), result.skipped(),
                result.failed(), result.originalBytes(), result.thumbnailBytes());
        if (result.failed() > 0) throw new IllegalStateException("Backfill has failed objects; inspect summary before retrying");
    }

    static Summary run(Storage storage, String bucket, int limit, boolean apply) {
        int scanned = 0, candidates = 0, created = 0, skipped = 0, failed = 0;
        long originalBytes = 0, thumbnailBytes = 0;
        outer: for (String prefix : new String[]{"items/", "collection-items/"}) {
            for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(prefix),
                    Storage.BlobListOption.pageSize(Math.min(limit, 100))).iterateAll()) {
                if (candidates >= limit) break outer;
                scanned++;
                if (blob.getName().endsWith("/") || blob.getSize() > 100L * 1024 * 1024) { skipped++; continue; }
                String key = ListThumbnail.key(blob.getName());
                try {
                    if (storage.get(BlobId.of(bucket, key)) != null) { skipped++; continue; }
                    candidates++;
                    if (!apply) continue;
                    byte[] bytes = blob.getContent(Blob.BlobSourceOption.generationMatch());
                    byte[] thumbnail = ListThumbnail.render(bytes);
                    if (thumbnail.length >= bytes.length) { skipped++; continue; }
                    // Do not create a derivative if the source changed or disappeared while rendering.
                    Blob current = storage.get(blob.getBlobId());
                    if (current == null || !blob.getGeneration().equals(current.getGeneration())) { skipped++; continue; }
                    storage.create(BlobInfo.newBuilder(bucket, key).setContentType("image/jpeg")
                            .setCacheControl("private, max-age=3600").build(), thumbnail,
                            Storage.BlobTargetOption.doesNotExist());
                    created++;
                    originalBytes += bytes.length;
                    thumbnailBytes += thumbnail.length;
                } catch (StorageException exception) {
                    if (exception.getCode() == 412 || exception.getCode() == 404) skipped++;
                    else { failed++; System.err.println("Failed: " + blob.getName() + " code=" + exception.getCode()); }
                } catch (Exception exception) {
                    failed++;
                    System.err.println("Failed: " + blob.getName() + " type=" + exception.getClass().getSimpleName());
                }
            }
        }
        return new Summary(scanned, candidates, created, skipped, failed, originalBytes, thumbnailBytes);
    }

    record Summary(int scanned, int candidates, int created, int skipped, int failed,
                   long originalBytes, long thumbnailBytes) {}
}
