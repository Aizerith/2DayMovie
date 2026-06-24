package com.example.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Cors cors = new Cors();
    private Frontend frontend = new Frontend();
    private Storage storage = new Storage();

    @Data
    public static class Cors {
        private String allowedOrigin;
    }

    @Data
    public static class Frontend {
        private String baseUrl;
    }

    @Data
    public static class Storage {
        private String endpoint;
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String publicUrlBase;
        private int presignedExpiryMinutes = 15;
        private long maxFileSizeBytes = 20L * 1024L * 1024L;
    }
}
