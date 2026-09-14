package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.config.S3Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(
        named = "S3_INTEGRATION_TEST",
        matches = "true"
)
class ImageServiceIntegrationTest {

    @Test
    void uploadDownloadAndDeleteImage() throws Exception {
        String bucket = System.getenv("S3_BUCKET");
        String region = System.getenv("AWS_REGION");

        assertNotNull(bucket, "S3_BUCKET 환경 변수를 설정하세요.");
        assertNotNull(region, "AWS_REGION 환경 변수를 설정하세요.");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png",
                output
        );
        byte[] imageBytes = output.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", imageBytes
        );

        try (S3Client s3 = new S3Config().s3Client(region)) {
            ImageService imageService = new ImageService(s3, bucket);

            String key = imageService.upload(file, ImageCategory.ITEM);

            try {
                assertThat(key).startsWith("items/").endsWith(".png");

                var downloaded = s3.getObjectAsBytes(
                        request -> request.bucket(bucket).key(key)
                );

                assertThat(downloaded.asByteArray()).isEqualTo(imageBytes);
                assertThat(downloaded.response().contentType())
                        .isEqualTo("image/png");
            } finally {
                imageService.delete(key);
            }

            S3Exception exception = assertThrows(
                    S3Exception.class,
                    () -> s3.headObject(
                            request -> request.bucket(bucket).key(key)
                    )
            );

            assertThat(exception.statusCode()).isEqualTo(404);
        }
    }
}