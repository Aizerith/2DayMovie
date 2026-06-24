package com.example.backend.common.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Frontend frontend = new Frontend();
    private Auth auth = new Auth();
    private Mail mail = new Mail();
    private Storage storage = new Storage();
    private Security security = new Security();

    @Data
    public static class Cors {
        private String allowedOrigin;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpirationMinutes;
        private long refreshTokenExpirationDays;
    }

    @Data
    public static class Frontend {
        private String baseUrl;
    }

    @Data
    public static class Auth {
        private long passwordResetTokenExpirationMinutes;
        private long emailVerificationTokenExpirationMinutes;
        private long expiredTokenCleanupFixedDelayMs = 86_400_000L;
    }

    @Data
    public static class Security {
        private RateLimit rateLimit = new RateLimit();
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int maxRequests = 10;
        private int windowSeconds = 60;
    }

    @Data
    public static class Mail {
        private boolean smtpEnabled;
        private String fromAddress;
        private boolean devInboxEnabled;
        private int devInboxMaxEntries;
    }

    @Data
    public static class Storage {
        private String endpoint;
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private int presignedExpiryMinutes = 15;
        private long maxFileSizeBytes = 20L * 1024L * 1024L;
        private List<String> allowedContentTypes = new ArrayList<>();
    }
}
