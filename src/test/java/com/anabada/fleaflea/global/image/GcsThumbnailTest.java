package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GcsThumbnailTest {
    private final Storage storage = mock(Storage.class);
    private final GcsImageStorage images = new GcsImageStorage(storage, "bucket", Duration.ofHours(1));

    private void signUrls() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), eq(3600L), eq(TimeUnit.SECONDS), any(Storage.SignUrlOption.class)))
                .thenAnswer(call -> URI.create("https://example.test/" + ((BlobInfo) call.getArgument(0)).getName()).toURL());
    }

    @Test void batchSelectsThumbnailsAndFallsBackWithoutPerItemMetadataRequests() throws Exception {
        signUrls();
        when(storage.get(anyList())).thenReturn(Arrays.asList(mock(Blob.class), null));
        var result = images.getListUrls(Arrays.asList("items/a.jpg", "items/b.jpg", "items/a.jpg", null));
        assertThat(result).containsEntry("items/a.jpg", "https://example.test/thumbnails/v1/items/a.jpg.jpg")
                .containsEntry("items/b.jpg", "https://example.test/items/b.jpg");
        images.getListUrls(List.of("items/a.jpg", "items/b.jpg"));
        verify(storage, times(1)).get(anyList());
        verify(storage, never()).get(any(BlobId.class));
    }

    @Test void metadataFailureFallsBackAndRetriesNextTime() throws Exception {
        signUrls();
        when(storage.get(anyList())).thenThrow(new StorageException(503, "down"))
                .thenReturn(List.of(mock(Blob.class)));
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .contains("thumbnails/v1/");
    }

    @Test void uploadStoresSmallerDerivativeAndDetailRetainsOriginal() throws Exception {
        signUrls();
        byte[] original = ListThumbnailTest.image(1600, 1000, false);
        images.upload("collection-items/a.jpg", original, "image/jpeg");
        var info = ArgumentCaptor.forClass(BlobInfo.class);
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage, times(2)).create(info.capture(), bytes.capture());
        assertThat(info.getAllValues().get(1).getName()).isEqualTo(ListThumbnail.key("collection-items/a.jpg"));
        assertThat(bytes.getAllValues().get(1).length).isLessThan(original.length);
        assertThat(images.getListUrls(List.of("collection-items/a.jpg")).get("collection-items/a.jpg"))
                .contains("thumbnails/v1/");
        assertThat(images.getUrl("collection-items/a.jpg")).isEqualTo("https://example.test/collection-items/a.jpg");
        verify(storage, never()).get(anyList());
    }

    @Test void derivativeWriteFailureDoesNotFailOriginalUpload() throws Exception {
        signUrls();
        when(storage.create(argThat((BlobInfo info) -> info.getName().startsWith("thumbnails/")), any(byte[].class)))
                .thenThrow(new StorageException(503, "down"));
        images.upload("items/a.jpg", ListThumbnailTest.image(1000, 800, false), "image/jpeg");
        verify(storage).create(argThat((BlobInfo info) -> info.getName().equals("items/a.jpg")), any(byte[].class));
        when(storage.get(anyList())).thenReturn(Arrays.asList((Blob) null));
        assertThat(images.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
    }

    @Test void missingDerivativeIsDiscoveredAfterBackfillAndCacheExpiry() throws Exception {
        signUrls();
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var cached = new GcsImageStorage(storage, "bucket", Duration.ofHours(1), clock::get);
        when(storage.get(anyList())).thenReturn(Arrays.asList((Blob) null)).thenReturn(List.of(mock(Blob.class)));
        assertThat(cached.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .isEqualTo("https://example.test/items/a.jpg");
        clock.addAndGet(Duration.ofSeconds(61).toNanos());
        assertThat(cached.getListUrls(List.of("items/a.jpg")).get("items/a.jpg"))
                .contains("thumbnails/v1/");
    }

    @Test void copyIncludesAvailableDerivative() {
        var writer = mock(com.google.cloud.storage.CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(writer);
        when(storage.get(BlobId.of("bucket", ListThumbnail.key("collection-items/source.jpg"))))
                .thenReturn(mock(Blob.class));
        images.copy("collection-items/source.jpg", "items/target.jpg");
        var requests = ArgumentCaptor.forClass(Storage.CopyRequest.class);
        verify(storage, times(2)).copy(requests.capture());
        assertThat(requests.getAllValues().get(1).getSource())
                .isEqualTo(BlobId.of("bucket", ListThumbnail.key("collection-items/source.jpg")));
        assertThat(requests.getAllValues().get(1).getTarget().getBlobId())
                .isEqualTo(BlobId.of("bucket", ListThumbnail.key("items/target.jpg")));
        verify(writer, times(2)).getResult();
    }

    @Test void deleteRemovesDerivativeButOptionalCleanupFailureDoesNotMaskOriginalSuccess() {
        when(storage.delete(BlobId.of("bucket", ListThumbnail.key("items/a.jpg"))))
                .thenThrow(new StorageException(503, "down"));
        assertThatCode(() -> images.delete("items/a.jpg")).doesNotThrowAnyException();
        verify(storage).delete(BlobId.of("bucket", "items/a.jpg"));
        verify(storage).delete(BlobId.of("bucket", ListThumbnail.key("items/a.jpg")));
    }
}
