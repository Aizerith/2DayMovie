package com.example.backend.storage.config;

import com.example.backend.common.config.AppProperties;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    @Qualifier("internalMinioClient")
    public MinioClient internalMinioClient(AppProperties appProperties) {
        AppProperties.Storage storage = appProperties.getStorage();

        return MinioClient.builder()
                .endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(AppProperties appProperties) {
        AppProperties.Storage storage = appProperties.getStorage();

        return MinioClient.builder()
                .endpoint(storage.getPublicEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }
}
