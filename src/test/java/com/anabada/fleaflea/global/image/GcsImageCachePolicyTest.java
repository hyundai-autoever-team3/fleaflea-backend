package com.anabada.fleaflea.global.image;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class GcsImageCachePolicyTest {

    @Test
    @DisplayName("브라우저 캐시는 최대 5분이며 짧은 서명 유효 시간을 넘지 않는다")
    void createCacheControl_capsBrowserCacheDuration() {
        assertThat(GcsImageCachePolicy.createCacheControl(Duration.ofHours(1)))
                .isEqualTo("private, max-age=300, must-revalidate");
        assertThat(GcsImageCachePolicy.createCacheControl(Duration.ofSeconds(30)))
                .isEqualTo("private, max-age=30, must-revalidate");
        assertThat(GcsImageCachePolicy.createCacheControl(Duration.ofHours(1), 0))
                .isEqualTo("private, max-age=0, must-revalidate");
    }

    @Test
    @DisplayName("허용 범위 밖의 캐시 시간과 서명 유효 시간은 거부한다")
    void createCacheControl_rejectsInvalidDuration() {
        assertThatThrownBy(() -> GcsImageCachePolicy.createCacheControl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GcsImageCachePolicy.createCacheControl(Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GcsImageCachePolicy.createCacheControl(Duration.ofHours(1), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GcsImageCachePolicy.createCacheControl(Duration.ofHours(1), 301))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상품·도감 원본만 대상으로 하고 프로필과 썸네일은 제외한다")
    void supports_limitsOriginalImageScope() {
        assertThat(GcsImageCachePolicy.supports("items/a.jpg")).isTrue();
        assertThat(GcsImageCachePolicy.supports("collection-items/a.webp")).isTrue();
        assertThat(GcsImageCachePolicy.supports("profiles/a.jpg")).isFalse();
        assertThat(GcsImageCachePolicy.supports("thumbnails/v1/items/a.jpg.jpg")).isFalse();
        assertThat(GcsImageCachePolicy.supports("items/directory/")).isFalse();
        assertThat(GcsImageCachePolicy.supports(null)).isFalse();
    }

    @Test
    @DisplayName("원본 업로드에 비공개 캐시 정책을 저장하고 프로필 정책은 변경하지 않는다")
    void upload_appliesPolicyOnlyToOriginalImages() {
        Storage storage = mock(Storage.class);
        GcsImageStorage imageStorage = new GcsImageStorage(storage, "bucket", Duration.ofHours(1));
        byte[] imageBytes = {1};
        imageStorage.upload("items/a.jpg", imageBytes, "image/jpeg");
        imageStorage.upload("profiles/a.jpg", imageBytes, "image/jpeg");
        ArgumentCaptor<BlobInfo> metadataCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage, times(2)).create(metadataCaptor.capture(), eq(imageBytes));
        assertThat(metadataCaptor.getAllValues().getFirst().getCacheControl())
                .isEqualTo("private, max-age=300, must-revalidate");
        assertThat(metadataCaptor.getAllValues().getLast().getCacheControl()).isNull();
    }

    @Test
    @DisplayName("원본 복사는 메타데이터를 덮어쓰지 않고 원본 정책을 상속한다")
    void copy_preservesSourceMetadataInheritance() {
        Storage storage = mock(Storage.class);
        CopyWriter copyWriter = mock(CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(copyWriter);
        GcsImageStorage imageStorage = new GcsImageStorage(storage, "bucket", Duration.ofSeconds(30));
        imageStorage.copy("collection-items/a.jpg", "items/b.jpg");
        ArgumentCaptor<Storage.CopyRequest> requestCaptor = ArgumentCaptor.forClass(Storage.CopyRequest.class);
        verify(storage).copy(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTarget().getCacheControl())
                .isNull();
        verify(copyWriter).getResult();
    }
}
