package com.anabada.fleaflea.global.image;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageCacheMetadataBackfillTest {

    private static final String CACHE_CONTROL = "private, max-age=300, must-revalidate";
    private final Storage storage = mock(Storage.class);

    @Test
    @DisplayName("미리보기는 객체 내용 다운로드와 메타데이터 갱신을 하지 않는다")
    void run_dryRunDoesNotWriteOrDownload() {
        Blob originalBlob = createOriginalBlob("items/a.jpg");
        stubListing(originalBlob);
        ImageCacheMetadataBackfill.Summary summary = ImageCacheMetadataBackfill.run(
                storage, "bucket", 1, CACHE_CONTROL, false
        );
        assertThat(summary.candidates()).isEqualTo(1);
        assertThat(summary.updated()).isZero();
        verify(originalBlob, never()).getContent(any(Blob.BlobSourceOption.class));
        verify(storage, never()).update(any(BlobInfo.class), any(Storage.BlobTargetOption.class), any(Storage.BlobTargetOption.class));
    }

    @Test
    @DisplayName("실제 갱신은 내용과 다른 메타데이터 없이 캐시 정책과 세대 조건만 전달한다")
    void run_updatesOnlyCacheControlWithGenerationPreconditions() {
        Blob originalBlob = createOriginalBlob("items/a.jpg");
        stubListing(originalBlob);
        ImageCacheMetadataBackfill.Summary summary = ImageCacheMetadataBackfill.run(
                storage, "bucket", 1, CACHE_CONTROL, true
        );
        ArgumentCaptor<BlobInfo> patchCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).update(patchCaptor.capture(),
                eq(Storage.BlobTargetOption.generationMatch(10L)),
                eq(Storage.BlobTargetOption.metagenerationMatch(2L)));
        assertThat(patchCaptor.getValue().getCacheControl()).isEqualTo(CACHE_CONTROL);
        assertThat(patchCaptor.getValue().getContentType()).isNull();
        assertThat(patchCaptor.getValue().getMetadata()).isNull();
        assertThat(summary.updated()).isEqualTo(1);
        verify(originalBlob, never()).getContent(any(Blob.BlobSourceOption.class));
    }

    @Test
    @DisplayName("이미 같은 정책인 객체와 폴더는 건너뛰고 신규 후보에만 제한을 적용한다")
    void run_skipsUnchangedObjectsAndFolders() {
        Blob unchangedBlob = createOriginalBlob("items/old.jpg");
        when(unchangedBlob.getCacheControl()).thenReturn(CACHE_CONTROL);
        stubListing(createOriginalBlob("items/folder/"), unchangedBlob, createOriginalBlob("items/new.jpg"));
        ImageCacheMetadataBackfill.Summary summary = ImageCacheMetadataBackfill.run(
                storage, "bucket", 1, CACHE_CONTROL, false
        );
        assertThat(summary.unchanged()).isEqualTo(1);
        assertThat(summary.candidates()).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제·변경 경합과 권한 오류를 구분해 집계한다")
    void run_reportsRacesAndFailures() {
        stubListing(createOriginalBlob("items/a.jpg"), createOriginalBlob("items/b.jpg"));
        when(storage.update(any(BlobInfo.class), any(Storage.BlobTargetOption.class), any(Storage.BlobTargetOption.class)))
                .thenThrow(new StorageException(412, "changed"))
                .thenThrow(new StorageException(403, "forbidden"));
        ImageCacheMetadataBackfill.Summary summary = ImageCacheMetadataBackfill.run(
                storage, "bucket", 2, CACHE_CONTROL, true
        );
        assertThat(summary.raced()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.updated()).isZero();
    }

    private Blob createOriginalBlob(String imageKey) {
        Blob originalBlob = mock(Blob.class);
        when(originalBlob.getName()).thenReturn(imageKey);
        when(originalBlob.getBlobId()).thenReturn(BlobId.of("bucket", imageKey));
        when(originalBlob.getGeneration()).thenReturn(10L);
        when(originalBlob.getMetageneration()).thenReturn(2L);
        return originalBlob;
    }

    @SuppressWarnings("unchecked")
    private void stubListing(Blob... originalBlobs) {
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(originalBlobs));
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class), any(Storage.BlobListOption.class)))
                .thenReturn(page);
    }
}
