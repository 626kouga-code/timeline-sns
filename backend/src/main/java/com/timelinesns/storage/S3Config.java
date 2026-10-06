package com.timelinesns.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * S3 クライアント。{@code app.storage.endpoint} があれば S3 互換ストレージ（S3Mock）につなぐ。
 * 本番（endpoint なし）は AWS SDK の既定の認証情報（EC2 のインスタンスロールなど）を使う。
 */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        String endpoint = properties.endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            // S3Mock は認証情報を検証しないので、固定のダミー値でよい。
            // バケット名をホスト名に含める形式（virtual-hosted style）は localhost で使えないため path-style にする
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }
}
