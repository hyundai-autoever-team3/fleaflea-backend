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
}
