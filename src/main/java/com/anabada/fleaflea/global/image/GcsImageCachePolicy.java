package com.anabada.fleaflea.global.image;

import java.time.Duration;

/** 비공개 원본 이미지의 브라우저 캐시 정책을 생성한다. */
public final class GcsImageCachePolicy {

    private static final long MAX_BROWSER_CACHE_SECONDS = 300;
    private static final Duration MAX_SIGNED_URL_DURATION = Duration.ofDays(7);

    private GcsImageCachePolicy() {
    }

    public static boolean supports(String imageKey) {
        return imageKey != null
                && (imageKey.startsWith("items/") || imageKey.startsWith("collection-items/"))
                && (imageKey.endsWith(".jpg") || imageKey.endsWith(".png") || imageKey.endsWith(".webp"));
    }

    public static String createCacheControl(Duration signedUrlDuration) {
        return createCacheControl(signedUrlDuration, MAX_BROWSER_CACHE_SECONDS);
    }

    public static String createCacheControl(
            Duration signedUrlDuration,
            long requestedMaxAgeSeconds
    ) {
        if (signedUrlDuration.getSeconds() < 1 || signedUrlDuration.compareTo(MAX_SIGNED_URL_DURATION) > 0) {
            throw new IllegalArgumentException("Signed URL duration must be between 1s and 7d");
        }
        if (requestedMaxAgeSeconds < 0 || requestedMaxAgeSeconds > MAX_BROWSER_CACHE_SECONDS) {
            throw new IllegalArgumentException("Browser cache max-age must be between 0s and 300s");
        }
        long maxAgeSeconds = Math.min(requestedMaxAgeSeconds, signedUrlDuration.getSeconds());
        return "private, max-age=" + maxAgeSeconds + ", must-revalidate";
    }
}
