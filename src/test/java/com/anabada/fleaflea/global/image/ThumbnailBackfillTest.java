package com.anabada.fleaflea.global.image;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("미리보기 실행은 원본 다운로드와 파일 쓰기를 하지 않는다")
    void dryRunDoesNotDownloadOrWrite() {
        Blob source = source("items/a.jpg");
        listing(source);
        ThumbnailBackfill.Summary summary = ThumbnailBackfill.run(storage, "bucket", 1, false);
        assertThat(summary.candidates()).isEqualTo(1);
        assertThat(summary.created()).isZero();
        verify(source, never()).getContent(any(Blob.BlobSourceOption.class));
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class));
    }

    @Test
    @DisplayName("기존 썸네일은 신규 생성 후보 제한에 포함하지 않는다")
    void existingDerivativesDoNotConsumeCandidateLimit() {
        Blob old = source("items/old.jpg");
        Blob fresh = source("items/new.jpg");
        listing(old, fresh);
        when(storage.get(BlobId.of("bucket", ListThumbnail.createThumbnailKey("items/old.jpg")))).thenReturn(mock(Blob.class));
        ThumbnailBackfill.Summary summary = ThumbnailBackfill.run(storage, "bucket", 1, false);
        assertThat(summary.scanned()).isEqualTo(2);
        assertThat(summary.candidates()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 생성은 원본 세대를 확인하고 기존 파생 파일을 덮어쓰지 않는다")
    void applyUsesGenerationAndDoesNotOverwriteExistingDerivative() throws Exception {
        Blob source = source("items/a.jpg");
        listing(source);
        when(source.getBlobId()).thenReturn(BlobId.of("bucket", "items/a.jpg"));
        when(source.getGeneration()).thenReturn(194L);
        when(source.getContent(any(Blob.BlobSourceOption.class)))
                .thenReturn(ListThumbnailTest.createImageBytes(1200, 800, false));
        when(storage.get(source.getBlobId())).thenReturn(source);
        ThumbnailBackfill.Summary summary = ThumbnailBackfill.run(storage, "bucket", 1, true);
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.thumbnailBytes()).isLessThan(summary.originalBytes());
        verify(source).getContent(Blob.BlobSourceOption.generationMatch());
        verify(storage).create(argThat((BlobInfo info) -> info.getName().equals(ListThumbnail.createThumbnailKey("items/a.jpg"))
                        && "private, max-age=300, must-revalidate".equals(info.getCacheControl())),
                any(byte[].class), eq(Storage.BlobTargetOption.doesNotExist()));
    }

    @Test
    @DisplayName("변환 중 삭제된 원본은 썸네일을 생성하지 않는다")
    void deletedSourceIsSkippedAfterRendering() throws Exception {
        Blob source = source("items/a.jpg");
        listing(source);
        when(source.getBlobId()).thenReturn(BlobId.of("bucket", "items/a.jpg"));
        when(source.getContent(any(Blob.BlobSourceOption.class)))
                .thenReturn(ListThumbnailTest.createImageBytes(1200, 800, false));
        ThumbnailBackfill.Summary summary = ThumbnailBackfill.run(storage, "bucket", 1, true);
        assertThat(summary.created()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class));
    }
}
