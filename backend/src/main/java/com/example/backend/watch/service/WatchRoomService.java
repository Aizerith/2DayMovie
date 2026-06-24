package com.example.backend.watch.service;

import com.example.backend.common.config.AppProperties;
import com.example.backend.watch.dto.AccessWatchRoomRequest;
import com.example.backend.watch.dto.CompleteWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomResponse;
import com.example.backend.watch.dto.PlaybackSyncMessage;
import com.example.backend.watch.dto.PresignedUpload;
import com.example.backend.watch.dto.UploadAssetRequest;
import com.example.backend.watch.dto.WatchRoomAccessResponse;
import com.example.backend.watch.entity.WatchRoom;
import com.example.backend.watch.entity.WatchRoomStatus;
import com.example.backend.watch.repository.WatchRoomRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WatchRoomService {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final String SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final WatchRoomRepository watchRoomRepository;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    @Transactional
    public CreateWatchRoomResponse create(CreateWatchRoomRequest request) {
        UploadAssetRequest video = request.video();
        UploadAssetRequest subtitle = request.subtitle();

        validateVideo(video);
        if (subtitle != null) {
            validateSubtitle(subtitle);
        }

        ensureBucketExists();

        WatchRoom room = new WatchRoom();
        room.setShareCode(generateShareCode());
        room.setTitle(StringUtils.hasText(request.title()) ? request.title().trim() : video.originalFilename());
        room.setPinHash(passwordEncoder.encode(request.pin()));
        room.setVideoOriginalFilename(normalizeOriginalFilename(video.originalFilename()));
        room.setVideoContentType(normalizeContentType(video.contentType()));
        room.setVideoObjectKey(generateObjectKey(room.getShareCode(), room.getVideoOriginalFilename()));
        room.setStatus(WatchRoomStatus.PENDING);

        if (subtitle != null) {
            room.setSubtitleOriginalFilename(normalizeOriginalFilename(subtitle.originalFilename()));
            room.setSubtitleContentType(normalizeContentType(subtitle.contentType()));
            room.setSubtitleObjectKey(generateObjectKey(room.getShareCode(), room.getSubtitleOriginalFilename()));
        }

        WatchRoom saved = watchRoomRepository.save(room);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(appProperties.getStorage().getPresignedExpiryMinutes());

        return new CreateWatchRoomResponse(
                saved.getShareCode(),
                appProperties.getFrontend().getBaseUrl() + "/watch/" + saved.getShareCode(),
                new PresignedUpload(generateUploadUrl(saved.getVideoObjectKey()), saved.getVideoObjectKey()),
                saved.getSubtitleObjectKey() == null ? null : new PresignedUpload(generateUploadUrl(saved.getSubtitleObjectKey()), saved.getSubtitleObjectKey()),
                expiresAt
        );
    }

    @Transactional
    public WatchRoomAccessResponse complete(String shareCode, CompleteWatchRoomRequest request) {
        WatchRoom room = findAndVerifyPin(shareCode, request.pin());
        StatObjectResponse videoStat = statObject(room.getVideoObjectKey());
        room.setVideoSizeBytes(videoStat.size());
        room.setVideoEtag(videoStat.etag());

        if (room.getSubtitleObjectKey() != null) {
            StatObjectResponse subtitleStat = statObject(room.getSubtitleObjectKey());
            room.setSubtitleSizeBytes(subtitleStat.size());
            room.setSubtitleEtag(subtitleStat.etag());
        }

        room.setStatus(WatchRoomStatus.READY);
        return toAccessResponse(room);
    }

    @Transactional(readOnly = true)
    public WatchRoomAccessResponse access(String shareCode, AccessWatchRoomRequest request) {
        WatchRoom room = findAndVerifyPin(shareCode, request.pin());
        if (room.getStatus() != WatchRoomStatus.READY) {
            throw new IllegalArgumentException("La video n est pas encore prete");
        }
        return toAccessResponse(room);
    }

    @Transactional
    public PlaybackSyncMessage synchronize(String shareCode, PlaybackSyncMessage message) {
        WatchRoom room = findAndVerifyPin(shareCode, message.pin());
        room.setPlaybackTimeSeconds(Math.max(0, message.currentTime()));
        room.setPlaying(message.playing());
        room.setPlaybackUpdatedAt(LocalDateTime.now());

        return new PlaybackSyncMessage(
                "",
                message.clientId(),
                room.getPlaybackTimeSeconds(),
                room.isPlaying(),
                message.event(),
                System.currentTimeMillis()
        );
    }

    private WatchRoomAccessResponse toAccessResponse(WatchRoom room) {
        return new WatchRoomAccessResponse(
                room.getShareCode(),
                room.getTitle(),
                generateDownloadUrl(room.getVideoObjectKey(), room.getVideoContentType()),
                room.getSubtitleObjectKey() == null ? null : generateDownloadUrl(room.getSubtitleObjectKey(), "text/vtt"),
                room.getVideoContentType(),
                room.getPlaybackTimeSeconds(),
                room.isPlaying()
        );
    }

    private WatchRoom findAndVerifyPin(String shareCode, String pin) {
        WatchRoom room = watchRoomRepository.findByShareCodeIgnoreCase(shareCode)
                .orElseThrow(() -> new IllegalArgumentException("Salon introuvable"));

        if (!passwordEncoder.matches(pin, room.getPinHash())) {
            throw new AccessDeniedException("Code PIN invalide");
        }

        return room;
    }

    private void ensureBucketExists() {
        String bucket = appProperties.getStorage().getBucket();

        try {
            boolean exists = internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if (!"BucketAlreadyOwnedByYou".equals(code) && !"BucketAlreadyExists".equals(code)) {
                throw new IllegalStateException("Object storage is unavailable", exception);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Object storage is unavailable", exception);
        }
    }

    private String generateUploadUrl(String objectKey) {
        try {
            String url = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .expiry(appProperties.getStorage().getPresignedExpiryMinutes(), TimeUnit.MINUTES)
                            .build()
            );
            return toPublicStorageUrl(url);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate an upload URL", exception);
        }
    }

    private String generateDownloadUrl(String objectKey, String contentType) {
        try {
            String url = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .expiry(appProperties.getStorage().getPresignedExpiryMinutes(), TimeUnit.MINUTES)
                            .extraQueryParams(Map.of("response-content-type", contentType))
                            .build()
            );
            return toPublicStorageUrl(url);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate a download URL", exception);
        }
    }

    private String toPublicStorageUrl(String presignedUrl) {
        String publicUrlBase = appProperties.getStorage().getPublicUrlBase();

        if (!StringUtils.hasText(publicUrlBase)) {
            return presignedUrl;
        }

        URI uri = URI.create(presignedUrl);
        String base = publicUrlBase.endsWith("/")
                ? publicUrlBase.substring(0, publicUrlBase.length() - 1)
                : publicUrlBase;
        return base + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private StatObjectResponse statObject(String objectKey) {
        try {
            return internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Le fichier uploade est introuvable dans MinIO", exception);
        }
    }

    private void validateVideo(UploadAssetRequest video) {
        String contentType = normalizeContentType(video.contentType());
        validateFileSize(video.sizeBytes());
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            throw new IllegalArgumentException("Le fichier video doit etre de type video/*");
        }
    }

    private void validateSubtitle(UploadAssetRequest subtitle) {
        String filename = normalizeOriginalFilename(subtitle.originalFilename()).toLowerCase(Locale.ROOT);
        String contentType = normalizeContentType(subtitle.contentType()).toLowerCase(Locale.ROOT);
        validateFileSize(subtitle.sizeBytes());
        if (!filename.endsWith(".vtt") && !"text/vtt".equals(contentType)) {
            throw new IllegalArgumentException("Le fichier sous-titre doit etre un .vtt");
        }
    }

    private void validateFileSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Le fichier ne doit pas etre vide");
        }
        if (sizeBytes > appProperties.getStorage().getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("Le fichier depasse la taille maximale autorisee");
        }
    }

    private String normalizeOriginalFilename(String originalFilename) {
        String candidate = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename.trim());
        if (!StringUtils.hasText(candidate) || candidate.contains("..")) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }
        int lastSlash = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        return lastSlash >= 0 ? candidate.substring(lastSlash + 1) : candidate;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
    }

    private String generateObjectKey(String shareCode, String originalFilename) {
        String sanitizedFilename = originalFilename
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-");
        String dateSegment = LocalDate.now().format(KEY_DATE_FORMATTER);
        return "rooms/" + shareCode + "/" + dateSegment + "/" + UUID.randomUUID() + "-" + sanitizedFilename;
    }

    private String generateShareCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder builder = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                builder.append(SHARE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(SHARE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (!watchRoomRepository.existsByShareCodeIgnoreCase(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
