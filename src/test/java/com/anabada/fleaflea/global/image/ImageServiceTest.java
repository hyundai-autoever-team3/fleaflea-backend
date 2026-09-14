package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.exception.ImageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final ImageService imageService =
            new ImageService(s3Client, "test-bucket");

    @Test
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

        try (var input =
                     bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(imageBytes);
        }
    }

    @Test
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
    void uploadRejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]
        );

        ImageException exception = assertThrows(
                ImageException.class,
                () -> imageService.upload(file, ImageCategory.ITEM)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        verifyNoInteractions(s3Client);
    }

    @Test
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
    void deleteUsesRequestedKey() {
        String key = "items/12345678-1234-1234-1234-123456789abc.png";

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
    void replaceDeletesPreviousImageAfterCommit() throws Exception {
        verifyReplacementCleanup(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    void replaceDeletesNewImageAfterRollback() throws Exception {
        verifyReplacementCleanup(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    void replaceRejectsMissingTransaction() throws Exception {
        MockMultipartFile file = createPngFile();

        assertThrows(
                IllegalStateException.class,
                () -> imageService.replace(
                        "items/12345678-1234-1234-1234-123456789abc.png",
                        file,
                        ImageCategory.ITEM
                )
        );

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

            var synchronizations =
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
}