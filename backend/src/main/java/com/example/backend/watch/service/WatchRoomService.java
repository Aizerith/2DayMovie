package com.example.backend.watch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.common.config.AppProperties;
import com.example.backend.watch.dto.AccessWatchRoomRequest;
import com.example.backend.watch.dto.AudioTrackResponse;
import com.example.backend.watch.dto.CompleteWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomResponse;
import com.example.backend.watch.dto.PlaybackSyncMessage;
import com.example.backend.watch.dto.PresignedUpload;
import com.example.backend.watch.dto.SubtitleTrackResponse;
import com.example.backend.watch.dto.UploadAssetRequest;
import com.example.backend.watch.dto.WatchRoomAccessResponse;
import com.example.backend.watch.entity.SubtitleTrackSource;
import com.example.backend.watch.entity.WatchAudioTrack;
import com.example.backend.watch.entity.WatchRoom;
import com.example.backend.watch.entity.WatchRoomStatus;
import com.example.backend.watch.entity.WatchSubtitleTrack;
import com.example.backend.watch.repository.WatchAudioTrackRepository;
import com.example.backend.watch.repository.WatchRoomRepository;
import com.example.backend.watch.repository.WatchSubtitleTrackRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchRoomService {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final String SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final ExecutorService VIDEO_PREPARATION_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "video-preparation");
        thread.setDaemon(true);
        return thread;
    });

    private final WatchRoomRepository watchRoomRepository;
    private final WatchSubtitleTrackRepository subtitleTrackRepository;
    private final WatchAudioTrackRepository audioTrackRepository;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final WatchPresenceService watchPresenceService;

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedVideoPreparations() {
        cleanupAbandonedPreparationDirectories();

        List<WatchRoom> interruptedRooms = watchRoomRepository.findAllByStatus(WatchRoomStatus.PROCESSING);
        if (interruptedRooms.isEmpty()) {
            return;
        }

        interruptedRooms.forEach(room -> {
            room.setStatus(WatchRoomStatus.FAILED);
            room.setPreparationMessage("Preparation interrompue");
            log.warn(
                    "Room {} was still processing at application startup and has been marked as FAILED",
                    room.getShareCode()
            );
        });
    }

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
        room.setPreparationProgressPercent(0);
        room.setPreparationMessage("En attente");

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

        refreshUploadedSubtitleTrack(room);
        audioTrackRepository.deleteAllByRoom(room);
        room.setStatus(WatchRoomStatus.PROCESSING);
        room.setPreparationProgressPercent(1);
        room.setPreparationMessage("Preparation lancee");
        log.info(
                "Room {} is queued for video preparation, source size {} bytes",
                room.getShareCode(),
                room.getVideoSizeBytes()
        );
        startVideoPreparation(room);
        return toAccessResponse(room);
    }

    @Transactional(readOnly = true)
    public WatchRoomAccessResponse access(String shareCode, AccessWatchRoomRequest request) {
        WatchRoom room = findAndVerifyPin(shareCode, request.pin());
        if (room.getStatus() == WatchRoomStatus.PENDING) {
            throw new IllegalArgumentException("La video n est pas encore prete");
        }
        log.info("Access granted for room {}", shareCode);
        return toAccessResponse(room);
    }

    @Transactional
    public WatchRoomAccessResponse retryPreparation(String shareCode, AccessWatchRoomRequest request) {
        WatchRoom room = findAndVerifyPin(shareCode, request.pin());
        if (room.getStatus() != WatchRoomStatus.FAILED) {
            throw new IllegalArgumentException("La conversion ne peut etre relancee que si elle a echoue");
        }

        StatObjectResponse videoStat = statObject(room.getVideoObjectKey());
        room.setVideoSizeBytes(videoStat.size());
        room.setVideoEtag(videoStat.etag());

        Set<String> objectKeysToDelete = new LinkedHashSet<>();
        if (StringUtils.hasText(room.getPlaybackVideoObjectKey())) {
            objectKeysToDelete.add(room.getPlaybackVideoObjectKey());
        }
        subtitleTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room)
                .stream()
                .map(WatchSubtitleTrack::getObjectKey)
                .filter(objectKey -> StringUtils.hasText(objectKey) && !objectKey.equals(room.getSubtitleObjectKey()))
                .forEach(objectKeysToDelete::add);
        audioTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room)
                .stream()
                .map(WatchAudioTrack::getObjectKey)
                .filter(StringUtils::hasText)
                .forEach(objectKeysToDelete::add);

        room.setPlaybackVideoObjectKey(null);
        room.setPlaybackVideoContentType(null);
        room.setPlaybackVideoSizeBytes(null);
        room.setPlaybackVideoEtag(null);
        refreshUploadedSubtitleTrack(room);
        audioTrackRepository.deleteAllByRoom(room);
        room.setStatus(WatchRoomStatus.PROCESSING);
        room.setPreparationProgressPercent(1);
        room.setPreparationMessage("Relance de la conversion");
        log.info("Room {} video preparation retry queued", room.getShareCode());

        deleteObjectsAfterCommit(room.getShareCode(), objectKeysToDelete);
        startVideoPreparation(room);
        return toAccessResponse(room);
    }

    @Transactional
    public void close(String shareCode, AccessWatchRoomRequest request) {
        log.info("Closing room {}", shareCode);
        WatchRoom room = findAndVerifyPin(shareCode, request.pin());
        Long roomId = room.getId();
        Set<String> objectKeys = new LinkedHashSet<>();

        objectKeys.add(room.getVideoObjectKey());
        if (StringUtils.hasText(room.getPlaybackVideoObjectKey())) {
            objectKeys.add(room.getPlaybackVideoObjectKey());
        }
        if (StringUtils.hasText(room.getSubtitleObjectKey())) {
            objectKeys.add(room.getSubtitleObjectKey());
        }

        subtitleTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room)
                .forEach(track -> objectKeys.add(track.getObjectKey()));
        audioTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room)
                .forEach(track -> objectKeys.add(track.getObjectKey()));

        audioTrackRepository.deleteAllByRoom(room);
        subtitleTrackRepository.deleteAllByRoom(room);
        audioTrackRepository.flush();
        subtitleTrackRepository.flush();
        watchRoomRepository.delete(room);
        watchRoomRepository.flush();

        if (watchRoomRepository.existsById(roomId)) {
            throw new IllegalStateException("Room could not be deleted");
        }

        notifyRoomClosedAfterCommit(shareCode, objectKeys);
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
        List<WatchSubtitleTrack> persistedSubtitleTracks = subtitleTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room);
        List<SubtitleTrackResponse> subtitleTracks = new ArrayList<>(persistedSubtitleTracks
                .stream()
                .map(track -> new SubtitleTrackResponse(
                        track.getLabel(),
                        track.getLanguage(),
                        generateDownloadUrl(track.getObjectKey(), "text/vtt")
                ))
                .toList());
        boolean hasUploadedSubtitleTrack = persistedSubtitleTracks.stream()
                .anyMatch(track -> room.getSubtitleObjectKey() != null && room.getSubtitleObjectKey().equals(track.getObjectKey()));
        if (StringUtils.hasText(room.getSubtitleObjectKey()) && !hasUploadedSubtitleTrack) {
            subtitleTracks.add(0, new SubtitleTrackResponse(
                    labelFromFilename(room.getSubtitleOriginalFilename(), "Sous-titres"),
                    "fr",
                    generateDownloadUrl(room.getSubtitleObjectKey(), "text/vtt")
            ));
        }
        List<AudioTrackResponse> audioTracks = audioTrackRepository.findAllByRoomOrderByDisplayOrderAsc(room)
                .stream()
                .map(track -> new AudioTrackResponse(
                        track.getLabel(),
                        track.getLanguage(),
                        generateDownloadUrl(track.getObjectKey(), "audio/mp4")
                ))
                .toList();

        return new WatchRoomAccessResponse(
                room.getShareCode(),
                room.getTitle(),
                room.getStatus() == WatchRoomStatus.READY
                        ? generateDownloadUrl(playbackVideoObjectKey(room), playbackVideoContentType(room))
                        : null,
                subtitleTracks.isEmpty() ? null : subtitleTracks.getFirst().url(),
                subtitleTracks,
                audioTracks,
                room.getStatus() == WatchRoomStatus.READY ? playbackVideoContentType(room) : null,
                room.getStatus().name(),
                room.getPreparationProgressPercent(),
                room.getPreparationMessage(),
                room.getPlaybackTimeSeconds(),
                room.isPlaying()
        );
    }

    private void refreshUploadedSubtitleTrack(WatchRoom room) {
        subtitleTrackRepository.deleteAllByRoom(room);

        List<WatchSubtitleTrack> tracks = new ArrayList<>();

        if (room.getSubtitleObjectKey() != null) {
            tracks.add(buildSubtitleTrack(
                    room,
                    labelFromFilename(room.getSubtitleOriginalFilename(), "Sous-titres"),
                    "fr",
                    room.getSubtitleObjectKey(),
                    SubtitleTrackSource.UPLOADED,
                    0
            ));
        }

        subtitleTrackRepository.saveAll(tracks);
    }

    private void startVideoPreparation(WatchRoom room) {
        RoomSubtitleExtractionRequest request = new RoomSubtitleExtractionRequest(
                room.getId(),
                room.getShareCode(),
                room.getVideoOriginalFilename(),
                room.getVideoObjectKey(),
                room.getSubtitleObjectKey() == null ? 0 : 1
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> prepareVideoAndStoreTracks(request), VIDEO_PREPARATION_EXECUTOR);
            }
        });
    }

    private void prepareVideoAndStoreTracks(RoomSubtitleExtractionRequest request) {
        PreparedRoomMedia preparedMedia = prepareRoomMedia(request);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            WatchRoom room = watchRoomRepository.findById(request.roomId()).orElse(null);
            if (room == null) {
                deletePreparedMedia(preparedMedia);
                return;
            }

            if (room.getStatus() != WatchRoomStatus.PROCESSING) {
                deletePreparedMedia(preparedMedia);
                return;
            }

            if (preparedMedia == null || !StringUtils.hasText(preparedMedia.playbackVideoObjectKey())) {
                room.setStatus(WatchRoomStatus.FAILED);
                room.setPreparationMessage("Preparation echouee");
                return;
            }

            room.setPlaybackVideoObjectKey(preparedMedia.playbackVideoObjectKey());
            room.setPlaybackVideoContentType("video/mp4");
            StatObjectResponse playbackStat = statObject(preparedMedia.playbackVideoObjectKey());
            room.setPlaybackVideoSizeBytes(playbackStat.size());
            room.setPlaybackVideoEtag(playbackStat.etag());

            saveExtractedTracks(room, preparedMedia.tracks(), request.firstOrder());
            room.setStatus(WatchRoomStatus.READY);
            room.setPreparationProgressPercent(100);
            room.setPreparationMessage("Pret");
            log.info(
                    "Prepared playback video for room {} with {} subtitle track(s) and {} audio track(s)",
                    request.shareCode(),
                    preparedMedia.tracks().subtitleTracks().size(),
                    preparedMedia.tracks().audioTracks().size()
            );
        });
    }

    private PreparedRoomMedia prepareRoomMedia(RoomSubtitleExtractionRequest request) {
        Path workingDirectory = null;

        try {
            workingDirectory = Files.createTempDirectory("2daymovie-prepare-");
            Path videoPath = workingDirectory.resolve("source-" + sanitizePathSegment(request.videoOriginalFilename()));
            Path playbackPath = workingDirectory.resolve("playback.mp4");
            updatePreparationProgress(request.roomId(), 2, "Preparation de l espace de travail");
            log.info("Room {} preparation started in {}", request.shareCode(), workingDirectory);
            log.info("Room {} downloading source video from storage", request.shareCode());
            downloadObject(request.videoObjectKey(), videoPath);
            updatePreparationProgress(request.roomId(), 8, "Video source telechargee");
            log.info("Room {} source video downloaded, local size {} bytes", request.shareCode(), Files.size(videoPath));

            VideoProbe videoProbe = probeVideo(videoPath);
            updatePreparationProgress(request.roomId(), 12, "Analyse de la video");
            log.info(
                    "Room {} source probe: container={}, videoCodec={}, audioCodec={}, webFriendly={}",
                    request.shareCode(),
                    videoProbe.container(),
                    videoProbe.videoCodec(),
                    videoProbe.audioCodec(),
                    videoProbe.webFriendly()
            );
            boolean prepared = videoProbe.webFriendly()
                    ? remuxVideo(videoPath, playbackPath, request, videoProbe)
                    : transcodeVideo(videoPath, playbackPath, request, videoProbe);

            if (!prepared || !Files.exists(playbackPath) || Files.size(playbackPath) == 0) {
                log.warn("Room {} playback video preparation produced no usable output", request.shareCode());
                return null;
            }
            updatePreparationProgress(request.roomId(), 86, "Video compatible generee");
            log.info("Room {} playback video prepared locally, size {} bytes", request.shareCode(), Files.size(playbackPath));

            if (!roomStillProcessing(request.roomId())) {
                log.info("Room {} is no longer processing before playback upload; discarding prepared media", request.shareCode());
                return null;
            }

            String playbackObjectKey = generatePlaybackObjectKey(request.shareCode());
            updatePreparationProgress(request.roomId(), 88, "Envoi de la video preparee");
            log.info("Room {} uploading playback video to storage", request.shareCode());
            uploadPlaybackVideo(playbackPath, playbackObjectKey);
            if (!roomStillProcessing(request.roomId())) {
                deleteObjectBestEffort(playbackObjectKey);
                log.info("Room {} was closed during playback upload; uploaded playback object deleted", request.shareCode());
                return null;
            }
            updatePreparationProgress(request.roomId(), 93, "Video preparee envoyee");
            log.info("Room {} playback video uploaded as {}", request.shareCode(), playbackObjectKey);

            updatePreparationProgress(request.roomId(), 95, "Extraction des pistes audio et sous-titres");
            log.info("Room {} extracting embedded subtitle and audio tracks", request.shareCode());
            ExtractedMediaTracks extractedTracks = extractEmbeddedMediaTracks(videoPath, workingDirectory, request);
            updatePreparationProgress(request.roomId(), 98, "Finalisation");
            log.info(
                    "Room {} extracted {} subtitle track(s) and {} audio track(s)",
                    request.shareCode(),
                    extractedTracks.subtitleTracks().size(),
                    extractedTracks.audioTracks().size()
            );
            return new PreparedRoomMedia(playbackObjectKey, extractedTracks);
        } catch (Exception exception) {
            log.warn("Unable to prepare playback video for room {}", request.shareCode(), exception);
            return null;
        } finally {
            deleteDirectoryQuietly(workingDirectory);
        }
    }

    private boolean roomStillProcessing(Long roomId) {
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status ->
                watchRoomRepository.findById(roomId)
                        .map(room -> room.getStatus() == WatchRoomStatus.PROCESSING)
                        .orElse(false)
        ));
    }

    private void saveExtractedTracks(WatchRoom room, ExtractedMediaTracks extractedTracks, int firstSubtitleOrder) {
        List<WatchSubtitleTrack> tracks = new ArrayList<>();
        int order = firstSubtitleOrder;

        for (ExtractedSubtitleTrack extractedTrack : extractedTracks.subtitleTracks()) {
            tracks.add(buildSubtitleTrack(
                    room,
                    extractedTrack.label(),
                    extractedTrack.language(),
                    extractedTrack.objectKey(),
                    SubtitleTrackSource.EXTRACTED,
                    order++
            ));
        }

        subtitleTrackRepository.saveAll(tracks);

        List<WatchAudioTrack> audioTracks = new ArrayList<>();
        int audioOrder = 0;
        for (ExtractedAudioTrack extractedTrack : extractedTracks.audioTracks()) {
            audioTracks.add(buildAudioTrack(
                    room,
                    extractedTrack.label(),
                    extractedTrack.language(),
                    extractedTrack.objectKey(),
                    audioOrder++
            ));
        }

        audioTrackRepository.deleteAllByRoom(room);
        audioTrackRepository.saveAll(audioTracks);
    }

    private void deleteExtractedMedia(ExtractedMediaTracks extractedTracks) {
        extractedTracks.subtitleTracks().forEach(track -> deleteObjectBestEffort(track.objectKey()));
        extractedTracks.audioTracks().forEach(track -> deleteObjectBestEffort(track.objectKey()));
    }

    private void deletePreparedMedia(PreparedRoomMedia preparedMedia) {
        if (preparedMedia == null) {
            return;
        }
        deleteObjectBestEffort(preparedMedia.playbackVideoObjectKey());
        deleteExtractedMedia(preparedMedia.tracks());
    }

    private void deleteObjectsAfterCommit(String shareCode, Set<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Room {} retry scheduled {} stale object(s) for deletion", shareCode, objectKeys.size());
                CompletableFuture.runAsync(() -> objectKeys.forEach(WatchRoomService.this::deleteObjectBestEffort));
            }
        });
    }

    private void notifyRoomClosedAfterCommit(String shareCode, Set<String> objectKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Room {} closed in database, {} object(s) scheduled for deletion", shareCode, objectKeys.size());
                messagingTemplate.convertAndSend(
                        "/topic/rooms/" + shareCode,
                        new PlaybackSyncMessage("", "server", 0, false, "closed", System.currentTimeMillis())
                );
                watchPresenceService.clearRoom(shareCode);
                CompletableFuture.runAsync(() -> objectKeys.forEach(WatchRoomService.this::deleteObjectBestEffort));
            }
        });
    }

    private ExtractedMediaTracks extractEmbeddedMediaTracks(RoomSubtitleExtractionRequest request) {
        Path workingDirectory = null;

        try {
            workingDirectory = Files.createTempDirectory("2daymovie-subtitles-");
            Path videoPath = workingDirectory.resolve("source-" + sanitizePathSegment(request.videoOriginalFilename()));
            downloadObject(request.videoObjectKey(), videoPath);
            return extractEmbeddedMediaTracks(videoPath, workingDirectory, request);
        } catch (Exception exception) {
            log.warn("Unable to extract embedded media tracks for room {}", request.shareCode(), exception);
            return new ExtractedMediaTracks(List.of(), List.of());
        } finally {
            deleteDirectoryQuietly(workingDirectory);
        }
    }

    private ExtractedMediaTracks extractEmbeddedMediaTracks(
            Path videoPath,
            Path workingDirectory,
            RoomSubtitleExtractionRequest request
    ) {
        try {
            List<ExtractedSubtitleTrack> subtitleTracks = new ArrayList<>();
            List<ExtractedAudioTrack> audioTracks = new ArrayList<>();

            for (EmbeddedSubtitleStream stream : probeSubtitleStreams(videoPath)) {
                Path outputPath = workingDirectory.resolve("subtitle-" + stream.index() + ".vtt");

                if (!extractSubtitle(videoPath, stream.index(), outputPath) || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
                    continue;
                }

                String objectKey = generateSubtitleObjectKey(request.shareCode(), stream.index(), stream.language());
                uploadSubtitle(outputPath, objectKey);
                subtitleTracks.add(new ExtractedSubtitleTrack(stream.label(), stream.language(), objectKey));
            }

            for (EmbeddedAudioStream stream : probeAudioStreams(videoPath)) {
                Path outputPath = workingDirectory.resolve("audio-" + stream.index() + ".m4a");

                if (!extractAudio(videoPath, stream.index(), outputPath) || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
                    continue;
                }

                String objectKey = generateAudioObjectKey(request.shareCode(), stream.index(), stream.language());
                uploadAudio(outputPath, objectKey);
                audioTracks.add(new ExtractedAudioTrack(stream.label(), stream.language(), objectKey));
            }

            return new ExtractedMediaTracks(subtitleTracks, audioTracks);
        } catch (Exception exception) {
            log.warn("Unable to extract embedded media tracks for room {}", request.shareCode(), exception);
            return new ExtractedMediaTracks(List.of(), List.of());
        }
    }

    private List<EmbeddedSubtitleStream> probeSubtitleStreams(Path videoPath) throws Exception {
        ProcessResult result = runProcess(List.of(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-select_streams", "s",
                videoPath.toString()
        ));

        if (result.exitCode() != 0 || !StringUtils.hasText(result.output())) {
            return List.of();
        }

        JsonNode streams = objectMapper.readTree(result.output()).path("streams");
        if (!streams.isArray()) {
            return List.of();
        }

        List<EmbeddedSubtitleStream> subtitleStreams = new ArrayList<>();
        int fallbackOrder = 1;

        for (JsonNode stream : streams) {
            int index = stream.path("index").asInt(-1);
            if (index < 0) {
                continue;
            }

            JsonNode tags = stream.path("tags");
            String language = normalizeLanguage(tags.path("language").asText(""));
            String title = tags.path("title").asText("");
            String label = StringUtils.hasText(title)
                    ? title.trim()
                    : "Sous-titres " + language.toUpperCase(Locale.ROOT);

            if (!StringUtils.hasText(label)) {
                label = "Sous-titres " + fallbackOrder;
            }

            subtitleStreams.add(new EmbeddedSubtitleStream(index, language, label));
            fallbackOrder++;
        }

        return subtitleStreams;
    }

    private VideoProbe probeVideo(Path videoPath) throws Exception {
        ProcessResult result = runProcess(List.of(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoPath.toString()
        ));

        if (result.exitCode() != 0 || !StringUtils.hasText(result.output())) {
            return new VideoProbe(false, "", "", "", 0);
        }

        JsonNode probe = objectMapper.readTree(result.output());
        JsonNode streams = probe.path("streams");
        if (!streams.isArray()) {
            return new VideoProbe(false, "", "", "", 0);
        }

        String container = probe.path("format").path("format_name").asText("");
        double durationSeconds = probe.path("format").path("duration").asDouble(0);
        String videoCodec = "";
        String audioCodec = "";

        for (JsonNode stream : streams) {
            String codecType = stream.path("codec_type").asText("");
            if ("video".equals(codecType) && !StringUtils.hasText(videoCodec)) {
                videoCodec = stream.path("codec_name").asText("");
            }
            if ("audio".equals(codecType) && !StringUtils.hasText(audioCodec)) {
                audioCodec = stream.path("codec_name").asText("");
            }
        }

        boolean videoReady = "h264".equalsIgnoreCase(videoCodec);
        boolean audioReady = !StringUtils.hasText(audioCodec)
                || "aac".equalsIgnoreCase(audioCodec)
                || "mp3".equalsIgnoreCase(audioCodec);
        return new VideoProbe(videoReady && audioReady, container, videoCodec, audioCodec, durationSeconds);
    }

    private List<EmbeddedAudioStream> probeAudioStreams(Path videoPath) throws Exception {
        ProcessResult result = runProcess(List.of(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-select_streams", "a",
                videoPath.toString()
        ));

        if (result.exitCode() != 0 || !StringUtils.hasText(result.output())) {
            return List.of();
        }

        JsonNode streams = objectMapper.readTree(result.output()).path("streams");
        if (!streams.isArray()) {
            return List.of();
        }

        List<EmbeddedAudioStream> audioStreams = new ArrayList<>();
        int fallbackOrder = 1;

        for (JsonNode stream : streams) {
            int index = stream.path("index").asInt(-1);
            if (index < 0) {
                continue;
            }

            JsonNode tags = stream.path("tags");
            String language = normalizeLanguage(tags.path("language").asText(""));
            String title = tags.path("title").asText("");
            String label = StringUtils.hasText(title)
                    ? title.trim()
                    : "Audio " + (StringUtils.hasText(language) && !"und".equals(language)
                            ? language.toUpperCase(Locale.ROOT)
                            : fallbackOrder);

            audioStreams.add(new EmbeddedAudioStream(index, language, label));
            fallbackOrder++;
        }

        return audioStreams;
    }

    private boolean extractSubtitle(Path videoPath, int streamIndex, Path outputPath) throws Exception {
        ProcessResult result = runProcess(List.of(
                "ffmpeg",
                "-v", "error",
                "-y",
                "-i", videoPath.toString(),
                "-map", "0:" + streamIndex,
                "-f", "webvtt",
                outputPath.toString()
        ));

        return result.exitCode() == 0;
    }

    private boolean extractAudio(Path videoPath, int streamIndex, Path outputPath) throws Exception {
        ProcessResult result = runProcess(List.of(
                "ffmpeg",
                "-v", "error",
                "-y",
                "-i", videoPath.toString(),
                "-map", "0:" + streamIndex,
                "-vn",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                outputPath.toString()
        ));

        return result.exitCode() == 0;
    }

    private boolean remuxVideo(
            Path videoPath,
            Path outputPath,
            RoomSubtitleExtractionRequest request,
            VideoProbe videoProbe
    ) throws Exception {
        ProcessResult result = runFfmpegProcessWithProgress(List.of(
                "ffmpeg",
                "-v", "error",
                "-y",
                "-progress", "pipe:1",
                "-nostats",
                "-i", videoPath.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c", "copy",
                "-movflags", "+faststart",
                outputPath.toString()
        ), request.roomId(), videoProbe.durationSeconds(), 15, 85, "Remux MP4");

        return result.exitCode() == 0;
    }

    private boolean transcodeVideo(
            Path videoPath,
            Path outputPath,
            RoomSubtitleExtractionRequest request,
            VideoProbe videoProbe
    ) throws Exception {
        ProcessResult result = runFfmpegProcessWithProgress(List.of(
                "ffmpeg",
                "-v", "error",
                "-y",
                "-progress", "pipe:1",
                "-nostats",
                "-i", videoPath.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "22",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                outputPath.toString()
        ), request.roomId(), videoProbe.durationSeconds(), 15, 85, "Conversion MP4 H.264/AAC");

        return result.exitCode() == 0;
    }

    private ProcessResult runProcess(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes());
        }
        return new ProcessResult(process.waitFor(), output);
    }

    private ProcessResult runFfmpegProcessWithProgress(
            List<String> command,
            Long roomId,
            double durationSeconds,
            int startPercent,
            int endPercent,
            String stage
    ) throws Exception {
        updatePreparationProgress(roomId, startPercent, stage);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        long lastProgressUpdate = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');

                Long outTimeMs = parseFfmpegOutTimeMs(line);
                if (outTimeMs == null || durationSeconds <= 0) {
                    continue;
                }

                long now = System.currentTimeMillis();
                if (now - lastProgressUpdate < TimeUnit.SECONDS.toMillis(3)) {
                    continue;
                }

                double mediaProgress = Math.min(1, Math.max(0, outTimeMs / 1_000_000.0 / durationSeconds));
                int percent = startPercent + (int) Math.floor(mediaProgress * (endPercent - startPercent));
                updatePreparationProgress(roomId, percent, stage + " " + percent + "%");
                lastProgressUpdate = now;
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            updatePreparationProgress(roomId, endPercent, stage + " terminee");
        }
        return new ProcessResult(exitCode, output.toString());
    }

    private Long parseFfmpegOutTimeMs(String line) {
        if (!line.startsWith("out_time_ms=")) {
            return null;
        }

        try {
            return Long.parseLong(line.substring("out_time_ms=".length()).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void updatePreparationProgress(Long roomId, int percent, String message) {
        int safePercent = Math.max(0, Math.min(100, percent));
        String safeMessage = StringUtils.hasText(message) ? message.trim() : "Preparation en cours";
        if (safeMessage.length() > 160) {
            safeMessage = safeMessage.substring(0, 160);
        }

        String finalMessage = safeMessage;
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                watchRoomRepository.findById(roomId)
                        .filter(room -> room.getStatus() == WatchRoomStatus.PROCESSING)
                        .ifPresent(room -> {
                            room.setPreparationProgressPercent(Math.max(room.getPreparationProgressPercent(), safePercent));
                            room.setPreparationMessage(finalMessage);
                        })
        );
    }

    private void downloadObject(String objectKey, Path targetPath) throws Exception {
        try (InputStream inputStream = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(appProperties.getStorage().getBucket())
                        .object(objectKey)
                        .build()
        )) {
            Files.copy(inputStream, targetPath);
        }
    }

    private void uploadSubtitle(Path subtitlePath, String objectKey) throws Exception {
        try (InputStream inputStream = Files.newInputStream(subtitlePath)) {
            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .contentType("text/vtt")
                            .stream(inputStream, Files.size(subtitlePath), -1)
                            .build()
            );
        }
    }

    private void uploadAudio(Path audioPath, String objectKey) throws Exception {
        try (InputStream inputStream = Files.newInputStream(audioPath)) {
            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .contentType("audio/mp4")
                            .stream(inputStream, Files.size(audioPath), -1)
                            .build()
            );
        }
    }

    private void uploadPlaybackVideo(Path playbackPath, String objectKey) throws Exception {
        try (InputStream inputStream = Files.newInputStream(playbackPath)) {
            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .contentType("video/mp4")
                            .stream(inputStream, Files.size(playbackPath), -1)
                            .build()
            );
        }
    }

    private void deleteObjectIfPresent(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }

        try {
            internalMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if (!"NoSuchKey".equals(code) && !"NoSuchBucket".equals(code)) {
                throw new IllegalStateException("Failed to delete a room file", exception);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete a room file", exception);
        }
    }

    private void deleteObjectBestEffort(String objectKey) {
        try {
            deleteObjectIfPresent(objectKey);
        } catch (Exception exception) {
            log.warn("Unable to delete object {} while closing room", objectKey, exception);
        }
    }

    private WatchSubtitleTrack buildSubtitleTrack(
            WatchRoom room,
            String label,
            String language,
            String objectKey,
            SubtitleTrackSource source,
            int displayOrder
    ) {
        WatchSubtitleTrack track = new WatchSubtitleTrack();
        track.setRoom(room);
        track.setLabel(StringUtils.hasText(label) ? label.trim() : "Sous-titres");
        track.setLanguage(normalizeLanguage(language));
        track.setObjectKey(objectKey);
        track.setSource(source);
        track.setDisplayOrder(displayOrder);
        return track;
    }

    private WatchAudioTrack buildAudioTrack(
            WatchRoom room,
            String label,
            String language,
            String objectKey,
            int displayOrder
    ) {
        WatchAudioTrack track = new WatchAudioTrack();
        track.setRoom(room);
        track.setLabel(StringUtils.hasText(label) ? label.trim() : "Audio");
        track.setLanguage(normalizeLanguage(language));
        track.setObjectKey(objectKey);
        track.setDisplayOrder(displayOrder);
        return track;
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

    private String generateSubtitleObjectKey(String shareCode, int streamIndex, String language) {
        String dateSegment = LocalDate.now().format(KEY_DATE_FORMATTER);
        return "rooms/" + shareCode + "/" + dateSegment + "/subtitles/" + UUID.randomUUID()
                + "-track-" + streamIndex + "-" + normalizeLanguage(language) + ".vtt";
    }

    private String generateAudioObjectKey(String shareCode, int streamIndex, String language) {
        String dateSegment = LocalDate.now().format(KEY_DATE_FORMATTER);
        return "rooms/" + shareCode + "/" + dateSegment + "/audio/" + UUID.randomUUID()
                + "-track-" + streamIndex + "-" + normalizeLanguage(language) + ".m4a";
    }

    private String generatePlaybackObjectKey(String shareCode) {
        String dateSegment = LocalDate.now().format(KEY_DATE_FORMATTER);
        return "rooms/" + shareCode + "/" + dateSegment + "/playback/" + UUID.randomUUID() + "-playback.mp4";
    }

    private String playbackVideoObjectKey(WatchRoom room) {
        return StringUtils.hasText(room.getPlaybackVideoObjectKey())
                ? room.getPlaybackVideoObjectKey()
                : room.getVideoObjectKey();
    }

    private String playbackVideoContentType(WatchRoom room) {
        return StringUtils.hasText(room.getPlaybackVideoContentType())
                ? room.getPlaybackVideoContentType()
                : room.getVideoContentType();
    }

    private String labelFromFilename(String filename, String fallback) {
        if (!StringUtils.hasText(filename)) {
            return fallback;
        }

        String cleaned = normalizeOriginalFilename(filename);
        int extension = cleaned.lastIndexOf('.');
        return extension > 0 ? cleaned.substring(0, extension) : cleaned;
    }

    private String normalizeLanguage(String language) {
        return StringUtils.hasText(language) ? language.trim().toLowerCase(Locale.ROOT) : "und";
    }

    private String sanitizePathSegment(String value) {
        String sanitized = value == null ? "video" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return StringUtils.hasText(sanitized) ? sanitized : "video";
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void cleanupAbandonedPreparationDirectories() {
        Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        if (!Files.isDirectory(tempDirectory)) {
            return;
        }

        try (var paths = Files.list(tempDirectory)) {
            paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("2daymovie-prepare-"))
                    .forEach(path -> {
                        log.info("Deleting abandoned preparation directory {}", path);
                        deleteDirectoryQuietly(path);
                    });
        } catch (Exception exception) {
            log.warn("Unable to clean abandoned preparation directories in {}", tempDirectory, exception);
        }
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

    private record EmbeddedSubtitleStream(int index, String language, String label) {
    }

    private record EmbeddedAudioStream(int index, String language, String label) {
    }

    private record VideoProbe(
            boolean webFriendly,
            String container,
            String videoCodec,
            String audioCodec,
            double durationSeconds
    ) {
    }

    private record PreparedRoomMedia(String playbackVideoObjectKey, ExtractedMediaTracks tracks) {
    }

    private record ExtractedMediaTracks(
            List<ExtractedSubtitleTrack> subtitleTracks,
            List<ExtractedAudioTrack> audioTracks
    ) {
    }

    private record ExtractedSubtitleTrack(String label, String language, String objectKey) {
    }

    private record ExtractedAudioTrack(String label, String language, String objectKey) {
    }

    private record RoomSubtitleExtractionRequest(
            Long roomId,
            String shareCode,
            String videoOriginalFilename,
            String videoObjectKey,
            int firstOrder
    ) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
