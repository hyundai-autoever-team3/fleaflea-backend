package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcsThumbnailTest {
    private final Storage storage = mock(Storage.class);
    private final GcsImageStorage images = new GcsImageStorage(storage, "bucket", Duration.ofHours(1));

    private void signUrls() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS), any(Storage.SignUrlOption.class)))
                .thenAnswer(call -> URI.create("https://example.test/" + ((BlobInfo) call.getArgument(0)).getName()).toURL());
    }

    @Test
    @DisplayName("썸네일을 일괄 조회하고 없는 이미지는 원본을 사용한다")
    void batchSelectsThumbnailsAndFallsBackWithoutPerItemMetadataRequests() throws Exception {
        signUrls();
        when(storage.get(anyList())).thenReturn(Arrays.asList(mock(Blob.class), null));
        Map<String, String> imageUrls = images.getListUrls(Arrays.asList("items/a.jpg", "items/b.jpg", "items/a.jpg", null));
        assertThat(imageUrls).containsEntry("items/a.jpg", "https://example.test/thumbnails/v1/items/a.jpg.jpg")
                .containsEntry("items/b.jpg", "https://example.test/items/b.jpg");
        images.getListUrls(List.of("items/a.jpg", "items/b.jpg"));
        verify(storage, times(1)).get(anyList());
        verify(storage, never()).get(any(BlobId.class));
    }

    @Test
    @DisplayName("존재 조회 실패 시 원본을 사용하고 다음 요청에서 재시도한다")
    void metadataFailureFallsBackAndRetriesNextTime() throws Exception {
        signUrls();
        when(storage.get(anyList())).thenThrow(new StorageException(503, "down"))
                .thenReturn(List.of(mock(Blob.class)));
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .contains("thumbnails/v1/");
    }

    @Test
    @DisplayName("업로드 시 작은 썸네일을 저장하고 상세 조회는 원본을 유지한다")
    void uploadStoresSmallerDerivativeAndDetailRetainsOriginal() throws Exception {
        signUrls();
        byte[] original = ListThumbnailTest.createImageBytes(1600, 1000, false);
        images.upload("collection-items/a.jpg", original, "image/jpeg");
        ArgumentCaptor<BlobInfo> info = ArgumentCaptor.forClass(BlobInfo.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage, times(2)).create(info.capture(), bytes.capture());
        assertThat(info.getAllValues().get(1).getName()).isEqualTo(ListThumbnail.createThumbnailKey("collection-items/a.jpg"));
        assertThat(info.getAllValues().get(1).getCacheControl())
                .isEqualTo("private, max-age=300, must-revalidate");
        assertThat(bytes.getAllValues().get(1).length).isLessThan(original.length);
        assertThat(images.getListUrls(List.of("collection-items/a.jpg")).get("collection-items/a.jpg"))
                .contains("thumbnails/v1/");
        assertThat(images.getUrl("collection-items/a.jpg")).isEqualTo("https://example.test/collection-items/a.jpg");
        verify(storage, never()).get(anyList());
    }

    @Test
    @DisplayName("썸네일 저장 실패는 원본 업로드 성공을 취소하지 않는다")
    void derivativeWriteFailureDoesNotFailOriginalUpload() throws Exception {
        signUrls();
        when(storage.create(argThat((BlobInfo info) -> info.getName().startsWith("thumbnails/")), any(byte[].class)))
                .thenThrow(new StorageException(503, "down"));
        images.upload("items/a.jpg", ListThumbnailTest.createImageBytes(1000, 800, false), "image/jpeg");
        verify(storage).create(argThat((BlobInfo info) -> info.getName().equals("items/a.jpg")), any(byte[].class));
        when(storage.get(anyList())).thenReturn(Arrays.asList((Blob) null));
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
    }

    @Test
    @DisplayName("존재 캐시 만료 후 생성된 썸네일을 조회한다")
    void missingDerivativeIsDiscoveredAfterBackfillAndCacheExpiry() throws Exception {
        signUrls();
        AtomicLong clock = new AtomicLong();
        GcsImageStorage cached = new GcsImageStorage(storage, "bucket", Duration.ofHours(1), clock::get);
        when(storage.get(anyList())).thenReturn(Arrays.asList((Blob) null)).thenReturn(List.of(mock(Blob.class)));
        assertThat(cached.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
        clock.addAndGet(Duration.ofSeconds(61).toNanos());
        assertThat(cached.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .contains("thumbnails/v1/");
    }

    @Test
    @DisplayName("원본 복사 시 존재하는 썸네일도 복사한다")
    void copyIncludesAvailableDerivative() {
        CopyWriter writer = mock(CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(writer);
        when(storage.get(BlobId.of("bucket", ListThumbnail.createThumbnailKey("collection-items/source.jpg"))))
                .thenReturn(mock(Blob.class));
        images.copy("collection-items/source.jpg", "items/target.jpg");
        ArgumentCaptor<Storage.CopyRequest> requests = ArgumentCaptor.forClass(Storage.CopyRequest.class);
        verify(storage, times(2)).copy(requests.capture());
        assertThat(requests.getAllValues().get(1).getSource())
                .isEqualTo(BlobId.of("bucket", ListThumbnail.createThumbnailKey("collection-items/source.jpg")));
        assertThat(requests.getAllValues().get(1).getTarget().getBlobId())
                .isEqualTo(BlobId.of("bucket", ListThumbnail.createThumbnailKey("items/target.jpg")));
        verify(writer, times(2)).getResult();
    }

    @Test
    @DisplayName("썸네일 삭제 실패는 원본 삭제 성공을 취소하지 않는다")
    void deleteRemovesDerivativeButOptionalCleanupFailureDoesNotMaskOriginalSuccess() {
        when(storage.delete(BlobId.of("bucket", ListThumbnail.createThumbnailKey("items/a.jpg"))))
                .thenThrow(new StorageException(503, "down"));
        assertThatCode(() -> images.delete("items/a.jpg")).doesNotThrowAnyException();
        verify(storage).delete(BlobId.of("bucket", "items/a.jpg"));
        verify(storage).delete(BlobId.of("bucket", ListThumbnail.createThumbnailKey("items/a.jpg")));
    }
}
