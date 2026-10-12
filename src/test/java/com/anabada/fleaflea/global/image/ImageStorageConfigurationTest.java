package com.anabada.fleaflea.global.image;

import com.anabada.fleaflea.global.config.S3Config;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImageStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(S3ImageStorage.class, GcsImageStorage.class, S3Config.class);

    @Test
    @DisplayName("저장소 설정이 없으면 기존 S3 연결을 유지한다")
    void defaultStorageRemainsS3() {
        contextRunner.withPropertyValues("aws.region=ap-northeast-2", "aws.s3.bucket=test-bucket")
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageStorage.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context.getBean(ImageStorage.class)).isInstanceOf(S3ImageStorage.class);
                });
    }

    @Test
    @DisplayName("GCS 선택 시 S3 환경변수 없이 GCS 저장소만 생성한다")
    void gcsStorageDoesNotRequireAwsConfiguration() {
        contextRunner.withBean(Storage.class, () -> mock(Storage.class))
                .withPropertyValues("app.image.storage=gcs", "gcp.storage.bucket=test-bucket")
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageStorage.class);
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context.getBean(ImageStorage.class)).isInstanceOf(GcsImageStorage.class);
                });
    }
}
