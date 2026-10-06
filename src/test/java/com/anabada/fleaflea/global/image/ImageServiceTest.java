package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final ImageService imageService =
            new ImageService(s3Client, "test-bucket");

    @Test
    @DisplayName("이미지 확장자와 MIME 타입은 실제 파일 내용으로 결정한다")
    void uploadUsesActualImageFormat() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferedImage image =
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", output);
        byte[] imageBytes = output.toByteArray();

        // 이름과 Content-Type이 달라도 실제 PNG 내용으로 판단해야 함!
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", imageBytes
        );

        String key = imageService.upload(file, ImageCategory.ITEM);

        assertThat(key).startsWith("items/").endsWith(".png");

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> requestCaptor =
                ArgumentCaptor.captor();
        ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(
                requestCaptor.capture(), bodyCaptor.capture()
        );

        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        requestCaptor.getValue().accept(builder);
        PutObjectRequest request = builder.build();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("image/png");

        try (InputStream input =
                     bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(imageBytes);
        }
    }

    @Test
    @DisplayName("실제 WebP 이미지는 WebP MIME 타입으로 업로드한다")
    void uploadAcceptsWebpAndSetsContentType() {
        byte[] imageBytes = Base64.getDecoder().decode(
                "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA=="
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", imageBytes
        );

        String key = imageService.upload(file, ImageCategory.ITEM);

        assertThat(key).startsWith("items/").endsWith(".webp");

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> requestCaptor =
                ArgumentCaptor.captor();
        verify(s3Client).putObject(
                requestCaptor.capture(), any(RequestBody.class)
        );

        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        requestCaptor.getValue().accept(builder);
        PutObjectRequest request = builder.build();

        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("SVG 파일은 업로드할 수 없다")
    void uploadRejectsSvg() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                        .getBytes(StandardCharsets.UTF_8)
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.ITEM)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("이미지로 위장한 텍스트 파일은 업로드할 수 없다")
    void uploadRejectsTextDisguisedAsImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png",
                "this is not an image".getBytes(StandardCharsets.UTF_8)
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.ITEM)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("제한 크기를 초과한 파일은 업로드할 수 없다")
    void uploadRejectsOversizedFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(100L * 1024 * 1024 + 1);

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.ITEM)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("빈 파일은 업로드할 수 없다")
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.PROFILE)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("잘못된 이미지 키는 삭제할 수 없다")
    void deleteRejectsInvalidKey() {
        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.delete("../other-file.png")
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("요청한 키의 이미지를 삭제한다")
    void deleteUsesRequestedKey() {
        String key = "items/12345678-1234-1234-1234-123456789abc.webp";

        imageService.delete(key);

        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor =
                ArgumentCaptor.captor();

        verify(s3Client).deleteObject(captor.capture());

        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        captor.getValue().accept(builder);
        DeleteObjectRequest request = builder.build();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("이미지 복사 시 WebP 확장자를 유지한다")
    void copyKeepsWebpExtension() {
        String sourceKey =
                "items/12345678-1234-1234-1234-123456789abc.webp";

        String targetKey = imageService.copy(
                sourceKey,
                ImageCategory.COLLECTION_ITEM
        );

        assertThat(targetKey)
                .startsWith("collection-items/")
                .endsWith(".webp");

        ArgumentCaptor<Consumer<CopyObjectRequest.Builder>> captor =
                ArgumentCaptor.captor();
        verify(s3Client).copyObject(captor.capture());

        CopyObjectRequest.Builder builder = CopyObjectRequest.builder();
        captor.getValue().accept(builder);
        CopyObjectRequest request = builder.build();

        assertThat(request.sourceBucket()).isEqualTo("test-bucket");
        assertThat(request.sourceKey()).isEqualTo(sourceKey);
        assertThat(request.destinationBucket()).isEqualTo("test-bucket");
        assertThat(request.destinationKey()).isEqualTo(targetKey);
    }

    @Test
    @DisplayName("DB 커밋 후에만 이미지를 삭제한다")
    void deleteAfterCommitDeletesImageOnlyAfterCommit() {
        String key = "items/12345678-1234-1234-1234-123456789abc.png";

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            imageService.deleteAfterCommit(key);

            verify(s3Client, never()).deleteObject(
                    org.mockito.ArgumentMatchers
                            .<Consumer<DeleteObjectRequest.Builder>>any()
            );

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.getFirst().afterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED
            );

            ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor =
                    ArgumentCaptor.captor();
            verify(s3Client).deleteObject(captor.capture());

            DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
            captor.getValue().accept(builder);
            assertThat(builder.build().key()).isEqualTo(key);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("DB 롤백 시 기존 이미지를 유지한다")
    void deleteAfterCommitKeepsImageAfterRollback() {
        String key = "items/12345678-1234-1234-1234-123456789abc.png";

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            imageService.deleteAfterCommit(key);

            TransactionSynchronizationManager.getSynchronizations()
                    .getFirst()
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(s3Client, never()).deleteObject(
                    org.mockito.ArgumentMatchers
                            .<Consumer<DeleteObjectRequest.Builder>>any()
            );
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("트랜잭션 없이 커밋 후 삭제를 요청하면 거절한다")
    void deleteAfterCommitRejectsMissingTransaction() {
        String key = "items/12345678-1234-1234-1234-123456789abc.png";

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.deleteAfterCommit(key)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TRANSACTION_REQUIRED);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("이미지 업로드 실패 시 원인 예외를 유지한다")
    void uploadPreservesS3FailureCause() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png",
                output
        );

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", output.toByteArray()
        );

        SdkClientException cause =
                SdkClientException.create("S3 connection failed");

        doThrow(cause).when(s3Client).putObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<PutObjectRequest.Builder>>any(),
                any(RequestBody.class)
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.ITEM)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("이미지 삭제 실패 시 원인 예외를 유지한다")
    void deletePreservesS3FailureCause() {
        String key = "items/12345678-1234-1234-1234-123456789abc.png";
        SdkClientException cause =
                SdkClientException.create("S3 connection failed");

        doThrow(cause).when(s3Client).deleteObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<DeleteObjectRequest.Builder>>any()
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.delete(key)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_DELETE_FAILED);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("교체 이미지 준비 중에는 기존 이미지를 삭제하지 않는다")
    void prepareReplacementKeepsPreviousImage() throws Exception {
        String previousKey =
                "items/12345678-1234-1234-1234-123456789abc.png";

        ImageReplacement result = imageService.prepareReplacement(
                previousKey, createPngFile(), ImageCategory.ITEM
        );

        assertThat(result.previousKey()).isEqualTo(previousKey);
        assertThat(result.newKey())
                .startsWith("items/")
                .endsWith(".png")
                .isNotEqualTo(previousKey);

        verify(s3Client).putObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<PutObjectRequest.Builder>>any(),
                any(RequestBody.class)
        );

        verify(s3Client, never()).deleteObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<DeleteObjectRequest.Builder>>any()
        );
    }

    @Test
    @DisplayName("교체 업로드에 실패하면 기존 이미지를 삭제하지 않는다")
    void prepareReplacementDoesNotDeleteOnUploadFailure() throws Exception {
        String previousKey =
                "items/12345678-1234-1234-1234-123456789abc.png";
        MockMultipartFile file = createPngFile();

        SdkClientException cause =
                SdkClientException.create("S3 upload failed");

        doThrow(cause).when(s3Client).putObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<PutObjectRequest.Builder>>any(),
                any(RequestBody.class)
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.prepareReplacement(
                        previousKey, file, ImageCategory.ITEM
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);
        assertThat(exception.getCause()).isSameAs(cause);

        verify(s3Client, never()).deleteObject(
                org.mockito.ArgumentMatchers
                        .<Consumer<DeleteObjectRequest.Builder>>any()
        );
    }

    @Test
    @DisplayName("기존 이미지와 교체 카테고리가 다르면 거절한다")
    void prepareReplacementRejectsDifferentCategory() throws Exception {
        String previousKey =
                "profiles/12345678-1234-1234-1234-123456789abc.png";
        MockMultipartFile file = createPngFile();

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.prepareReplacement(
                        previousKey, file, ImageCategory.ITEM
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);
        verifyNoInteractions(s3Client);
    }

    private MockMultipartFile createPngFile() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png",
                output
        );

        return new MockMultipartFile(
                "file", "replacement.png", "image/png",
                output.toByteArray()
        );
    }

    @Test
    @DisplayName("이미지 교체 커밋 후 기존 이미지를 삭제한다")
    void replaceDeletesPreviousImageAfterCommit() throws Exception {
        verifyReplacementCleanup(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    @DisplayName("이미지 교체 롤백 후 새 이미지를 삭제한다")
    void replaceDeletesNewImageAfterRollback() throws Exception {
        verifyReplacementCleanup(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    @DisplayName("트랜잭션 없이 이미지 교체를 요청하면 거절한다")
    void replaceRejectsMissingTransaction() throws Exception {
        MockMultipartFile file = createPngFile();

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.replace(
                        "items/12345678-1234-1234-1234-123456789abc.png",
                        file,
                        ImageCategory.ITEM
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TRANSACTION_REQUIRED);

        verifyNoInteractions(s3Client);
    }

    private void verifyReplacementCleanup(int completionStatus) throws Exception {
        String previousKey =
                "items/12345678-1234-1234-1234-123456789abc.png";

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            String newKey = imageService.replace(
                    previousKey, createPngFile(), ImageCategory.ITEM
            );

            assertThat(newKey).isNotEqualTo(previousKey);

            // DB 처리 결과가 나오기 전에는 어떤 이미지도 삭제하지 않는다!
            verify(s3Client, never()).deleteObject(
                    org.mockito.ArgumentMatchers
                            .<Consumer<DeleteObjectRequest.Builder>>any()
            );

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            // DB 커밋 또는 롤백 완료 상황을 만든다
            synchronizations.getFirst().afterCompletion(completionStatus);

            ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor =
                    ArgumentCaptor.captor();
            verify(s3Client).deleteObject(captor.capture());

            DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
            captor.getValue().accept(builder);

            String expectedKey =
                    completionStatus == TransactionSynchronization.STATUS_COMMITTED
                            ? previousKey
                            : newKey;

            assertThat(builder.build().key()).isEqualTo(expectedKey);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    @ParameterizedTest(name = "트랜잭션 완료 상태={0}")
    @ValueSource(ints = {TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_ROLLED_BACK,
            TransactionSynchronization.STATUS_UNKNOWN})
    @DisplayName("새 이미지 업로드는 롤백이 확정된 경우에만 새 파일을 삭제한다")
    void uploadInTransaction_cleansUpOnlyAfterRollback(int completionStatus) throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            String key = imageService.uploadInTransaction(createPngFile(), ImageCategory.COLLECTION_ITEM);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            verify(s3Client, never()).deleteObject(org.mockito.ArgumentMatchers.<Consumer<DeleteObjectRequest.Builder>>any());

            synchronizations.getFirst().afterCompletion(completionStatus);

            if (completionStatus == TransactionSynchronization.STATUS_ROLLED_BACK) {
                ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor = ArgumentCaptor.captor();
                verify(s3Client).deleteObject(captor.capture());
                DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
                captor.getValue().accept(builder);
                assertThat(builder.build().key()).isEqualTo(key);
            } else {
                verify(s3Client, never()).deleteObject(org.mockito.ArgumentMatchers.<Consumer<DeleteObjectRequest.Builder>>any());
            }
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("트랜잭션 없이 새 이미지의 롤백 정리를 요청하면 업로드 전에 거절한다")
    void uploadInTransaction_requiresWritableTransaction() throws Exception {
        MockMultipartFile image = createPngFile();

        ImageException exception = assertThrows(ImageException.class,
                () -> imageService.uploadInTransaction(image, ImageCategory.COLLECTION_ITEM));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TRANSACTION_REQUIRED);
        verifyNoInteractions(s3Client);
    }

}
