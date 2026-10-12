package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GcsImageStorageTest {

    private final Storage storage = mock(Storage.class);
    private final GcsImageStorage imageStorage =
            new GcsImageStorage(storage, "test-bucket", Duration.ofHours(1));

    @Test
    @DisplayName("GCS 업로드는 이미지 키와 검증된 MIME 타입을 보존한다")
    void uploadPreservesImageKeyAndContentType() {
        byte[] imageBytes = {1, 2, 3};

        imageStorage.upload("items/image.png", imageBytes, "image/png");

        ArgumentCaptor<BlobInfo> blobInfoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(blobInfoCaptor.capture(), eq(imageBytes));
        assertThat(blobInfoCaptor.getValue().getBucket()).isEqualTo("test-bucket");
        assertThat(blobInfoCaptor.getValue().getName()).isEqualTo("items/image.png");
        assertThat(blobInfoCaptor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("GCS 이미지 복사는 같은 버킷에서 완료될 때까지 기다린다")
    void copyWaitsForCompletion() {
        CopyWriter copyWriter = mock(CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(copyWriter);

        imageStorage.copy("items/source.png", "collection-items/target.png");

        ArgumentCaptor<Storage.CopyRequest> copyRequestCaptor =
                ArgumentCaptor.forClass(Storage.CopyRequest.class);
        verify(storage).copy(copyRequestCaptor.capture());
        assertThat(copyRequestCaptor.getValue().getSource())
                .isEqualTo(BlobId.of("test-bucket", "items/source.png"));
        assertThat(copyRequestCaptor.getValue().getTarget().getBlobId())
                .isEqualTo(BlobId.of("test-bucket", "collection-items/target.png"));
        verify(copyWriter).getResult();
    }

    @Test
    @DisplayName("이미 삭제된 GCS 이미지 삭제 요청도 성공한다")
    void deleteAllowsMissingObject() {
        imageStorage.delete("items/missing.png");

        verify(storage).delete(BlobId.of("test-bucket", "items/missing.png"));
    }

    @Test
    @DisplayName("GCS 업로드 장애를 기존 이미지 업로드 예외로 변환한다")
    void uploadConvertsStorageFailure() {
        byte[] imageBytes = {1};
        when(storage.create(any(BlobInfo.class), eq(imageBytes)))
                .thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> imageStorage.upload("items/image.png", imageBytes, "image/png"))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("GCS 삭제 장애를 기존 이미지 삭제 예외로 변환한다")
    void deleteConvertsStorageFailure() {
        when(storage.delete(BlobId.of("test-bucket", "items/image.png")))
                .thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> imageStorage.delete("items/image.png"))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_DELETE_FAILED);
    }

    @Test
    @DisplayName("비공개 GCS 이미지는 설정한 유효 기간의 V4 서명 URL로 반환한다")
    void getUrlUsesV4SignedUrl() throws Exception {
        BlobInfo blobInfo = BlobInfo.newBuilder("test-bucket", "items/image.png").build();
        when(storage.signUrl(eq(blobInfo), eq(3600L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class)))
                .thenReturn(URI.create("https://storage.googleapis.com/test-bucket/items/image.png?signature=test")
                        .toURL());

        assertThat(imageStorage.getUrl("items/image.png")).contains("signature=test");
        ArgumentCaptor<Storage.SignUrlOption> signUrlOptionCaptor =
                ArgumentCaptor.forClass(Storage.SignUrlOption.class);
        verify(storage).signUrl(eq(blobInfo), eq(3600L), eq(TimeUnit.SECONDS),
                signUrlOptionCaptor.capture());
        assertThat(signUrlOptionCaptor.getValue()).usingRecursiveComparison()
                .isEqualTo(Storage.SignUrlOption.withV4Signature());
    }

    @Test
    @DisplayName("비어 있는 버킷이나 지원 범위 밖의 서명 유효 기간이면 시작하지 않는다")
    void constructorRejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new GcsImageStorage(storage, "", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GcsImageStorage(storage, "test-bucket", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GcsImageStorage(storage, "test-bucket", Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("반복 조회는 URL을 재사용하고 조회가 계속되어도 만료 전에 갱신한다")
    void repeatedReadsDoNotExtendExpiration() throws Exception {
        AtomicLong elapsedNanos = new AtomicLong();
        GcsImageStorage cachedStorage = new GcsImageStorage(
                storage, "test-bucket", Duration.ofHours(1), elapsedNanos::get);
        stubSignedUrls();

        assertThat(cachedStorage.getUrl("items/image.png")).endsWith("first");
        elapsedNanos.set(TimeUnit.MINUTES.toNanos(58));
        assertThat(cachedStorage.getUrl("items/image.png")).endsWith("first");
        elapsedNanos.set(TimeUnit.MINUTES.toNanos(59));
        assertThat(cachedStorage.getUrl("items/image.png")).endsWith("second");
        verifySigningCount(2);
    }

    @Test
    @DisplayName("서명 요청에 걸린 시간도 캐시 유효 기간에 포함한다")
    void signingLatencyCountsTowardExpiration() throws Exception {
        AtomicLong elapsedNanos = new AtomicLong();
        GcsImageStorage cachedStorage = new GcsImageStorage(
                storage, "test-bucket", Duration.ofSeconds(10), elapsedNanos::get);
        when(storage.signUrl(any(BlobInfo.class), eq(10L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class))).thenAnswer(invocation -> {
                    elapsedNanos.addAndGet(TimeUnit.SECONDS.toNanos(8));
                    return URI.create("https://example.com/image").toURL();
                });

        cachedStorage.getUrl("items/image.png");
        elapsedNanos.addAndGet(TimeUnit.SECONDS.toNanos(1));
        cachedStorage.getUrl("items/image.png");
        verify(storage, times(2)).signUrl(any(BlobInfo.class), eq(10L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class));
    }

    @Test
    @DisplayName("서명 실패는 캐시하지 않고 다음 조회에서 재시도한다")
    void signingFailureIsRetried() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class)))
                .thenThrow(new StorageException(503, "unavailable"))
                .thenReturn(URI.create("https://example.com/recovered").toURL());

        assertThatThrownBy(() -> imageStorage.getUrl("items/image.png"))
                .isInstanceOf(StorageException.class);
        assertThat(imageStorage.getUrl("items/image.png")).endsWith("recovered");
        verifySigningCount(2);
    }

    @Test
    @DisplayName("이미지 업로드와 삭제 및 복사는 변경된 키의 URL 캐시를 무효화한다")
    void mutationsInvalidateCachedUrl() throws Exception {
        stubSignedUrls();
        CopyWriter copyWriter = mock(CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(copyWriter);

        imageStorage.getUrl("items/image.png");
        imageStorage.upload("items/image.png", new byte[]{1}, "image/png");
        imageStorage.getUrl("items/image.png");
        imageStorage.delete("items/image.png");
        imageStorage.getUrl("items/image.png");
        imageStorage.copy("items/source.png", "items/image.png");
        imageStorage.getUrl("items/image.png");

        verifySigningCount(4);
    }

    @Test
    @DisplayName("같은 이미지에 동시 요청이 들어와도 서명은 한 번만 요청한다")
    void concurrentReadsShareSigningRequest() throws Exception {
        CountDownLatch signingStarted = new CountDownLatch(1);
        CountDownLatch finishSigning = new CountDownLatch(1);
        when(storage.signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class))).thenAnswer(invocation -> {
                    signingStarted.countDown();
                    assertThat(finishSigning.await(5, TimeUnit.SECONDS)).isTrue();
                    return URI.create("https://example.com/shared").toURL();
                });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstUrl = executor.submit(() -> imageStorage.getUrl("items/image.png"));
            try {
                assertThat(signingStarted.await(5, TimeUnit.SECONDS)).isTrue();
                var secondUrl = executor.submit(() -> imageStorage.getUrl("items/image.png"));
                finishSigning.countDown();
                assertThat(firstUrl.get(5, TimeUnit.SECONDS)).isEqualTo(secondUrl.get(5, TimeUnit.SECONDS));
            } finally {
                finishSigning.countDown();
            }
        }
        verifySigningCount(1);
    }

    private void stubSignedUrls() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class)))
                .thenReturn(URI.create("https://example.com/first").toURL(),
                        URI.create("https://example.com/second").toURL());
    }

    @Test
    @DisplayName("유효 기간이 1초이면 만료 URL 재사용을 피하도록 캐시하지 않는다")
    void oneSecondValidityBypassesCache() throws Exception {
        GcsImageStorage shortLivedStorage = new GcsImageStorage(storage, "test-bucket", Duration.ofSeconds(1));
        when(storage.signUrl(any(BlobInfo.class), eq(1L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class)))
                .thenReturn(URI.create("https://example.com/short").toURL());

        shortLivedStorage.getUrl("items/image.png");
        shortLivedStorage.getUrl("items/image.png");

        verify(storage, times(2)).signUrl(any(BlobInfo.class), eq(1L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class));
    }

    @Test
    @DisplayName("다른 이미지 키는 각각 서명하고 변경 실패 시 기존 URL을 재사용한다")
    void differentKeysAndFailedMutationPreserveCache() throws Exception {
        stubSignedUrls();
        String originalUrl = imageStorage.getUrl("items/image.png");
        assertThat(imageStorage.getUrl("items/other.png")).isNotEqualTo(originalUrl);
        when(storage.delete(BlobId.of("test-bucket", "items/image.png")))
                .thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> imageStorage.delete("items/image.png"))
                .isInstanceOf(ImageException.class);
        assertThat(imageStorage.getUrl("items/image.png")).isEqualTo(originalUrl);
        verifySigningCount(2);
    }

    private void verifySigningCount(int count) {
        verify(storage, times(count)).signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class));
    }
}
