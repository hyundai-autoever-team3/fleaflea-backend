package com.anabada.fleaflea.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(
        named = "S3_INTEGRATION_TEST",
        matches = "true"
)
class S3ConnectionTest {

    @Test
    void uploadAndDownload() {
        String bucket = System.getenv("S3_BUCKET");
        String region = System.getenv("AWS_REGION");

        assertNotNull(bucket, "S3_BUCKET 환경 변수를 설정하세요.");
        assertNotNull(region, "AWS_REGION 환경 변수를 설정하세요.");

        String key = "test/java-connection-" + UUID.randomUUID() + ".txt";
        String content = "fleaflea-java-s3-test";

        try (S3Client s3 = new S3Config().s3Client(region)) {
            s3.putObject(
                    request -> request.bucket(bucket)
                            .key(key)
                            .contentType("text/plain"),
                    RequestBody.fromString(content)
            );

            try {
                String downloaded = s3.getObjectAsBytes(
                        request -> request.bucket(bucket).key(key)
                ).asUtf8String();

                assertEquals(content, downloaded);
            } finally {
                s3.deleteObject(
                        request -> request.bucket(bucket).key(key)
                );
            }
        }
    }
}