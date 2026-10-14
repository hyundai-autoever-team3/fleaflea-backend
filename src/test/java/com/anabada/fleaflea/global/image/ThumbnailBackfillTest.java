package com.anabada.fleaflea.global.image;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ThumbnailBackfillTest {
    private final Storage storage = mock(Storage.class);

    @SuppressWarnings("unchecked")
    private void listing(Blob... blobs) {
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(blobs));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class), any(Storage.BlobListOption.class)))
                .thenReturn(page);
    }

    private Blob source(String name) {
        Blob blob = mock(Blob.class);
        when(blob.getName()).thenReturn(name);
        when(blob.getSize()).thenReturn(10_000L);
        return blob;
    }

    @Test void dryRunDoesNotDownloadOrWrite() {
        Blob source = source("items/a.jpg");
        listing(source);
        var result = ThumbnailBackfill.run(storage, "bucket", 1, false);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.created()).isZero();
        verify(source, never()).getContent(any(Blob.BlobSourceOption.class));
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class));
    }

    @Test void existingDerivativesDoNotConsumeCandidateLimit() {
        Blob old = source("items/old.jpg"), fresh = source("items/new.jpg");
        listing(old, fresh);
        when(storage.get(BlobId.of("bucket", ListThumbnail.key("items/old.jpg")))).thenReturn(mock(Blob.class));
        var result = ThumbnailBackfill.run(storage, "bucket", 1, false);
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test void applyUsesGenerationAndDoesNotOverwriteExistingDerivative() throws Exception {
        Blob source = source("items/a.jpg");
        listing(source);
        when(source.getBlobId()).thenReturn(BlobId.of("bucket", "items/a.jpg"));
        when(source.getGeneration()).thenReturn(194L);
        when(source.getContent(any(Blob.BlobSourceOption.class)))
                .thenReturn(ListThumbnailTest.image(1200, 800, false));
        when(storage.get(source.getBlobId())).thenReturn(source);
        var result = ThumbnailBackfill.run(storage, "bucket", 1, true);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.thumbnailBytes()).isLessThan(result.originalBytes());
        verify(source).getContent(Blob.BlobSourceOption.generationMatch());
        verify(storage).create(argThat((BlobInfo info) -> info.getName().equals(ListThumbnail.key("items/a.jpg"))),
                any(byte[].class), eq(Storage.BlobTargetOption.doesNotExist()));
    }

    @Test void deletedSourceIsSkippedAfterRendering() throws Exception {
        Blob source = source("items/a.jpg");
        listing(source);
        when(source.getBlobId()).thenReturn(BlobId.of("bucket", "items/a.jpg"));
        when(source.getContent(any(Blob.BlobSourceOption.class)))
                .thenReturn(ListThumbnailTest.image(1200, 800, false));
        var result = ThumbnailBackfill.run(storage, "bucket", 1, true);
        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class));
    }
}
