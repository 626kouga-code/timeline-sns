package com.timelinesns;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト用の PostgreSQL と画像の保存先（S3Mock）を Testcontainers で起動する。
 * どちらも docker-compose と同じイメージ・バケット名を使う。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** 画像を保存するバケット（application.yml の app.storage.bucket の既定値と同じ） */
    public static final String BUCKET = "timeline-media";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }

    @Bean
    S3MockContainer s3MockContainer() {
        return new S3MockContainer("5.2").withInitialBuckets(BUCKET);
    }

    // S3Mock には Spring Boot の ServiceConnection が無いので、起動したコンテナの URL を設定に渡す
    @Bean
    DynamicPropertyRegistrar s3MockProperties(S3MockContainer s3Mock) {
        return registry -> registry.add("app.storage.endpoint", s3Mock::getHttpEndpoint);
    }
}
