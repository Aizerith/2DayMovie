package com.example.backend.storage.service;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.config.AppProperties;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.storage.dto.InitiateFileUploadRequest;
import com.example.backend.storage.dto.InitiateFileUploadResponse;
import com.example.backend.storage.dto.PresignedDownloadResponse;
import com.example.backend.storage.dto.StoredFileResponse;
import com.example.backend.storage.entity.StoredFile;
import com.example.backend.storage.entity.StoredFileStatus;
import com.example.backend.storage.exception.StoredFileNotFoundException;
import com.example.backend.storage.exception.StorageOperationException;
import com.example.backend.storage.repository.StoredFileRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StorageService {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final StoredFileRepository storedFileRepository;
    private final AppUserRepository appUserRepository;
    private final AppProperties appProperties;

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    @Transactional(readOnly = true)
    public PagedResponse<StoredFileResponse> findPageForUser(String email, Pageable pageable) {
        AppUser currentUser = getCurrentUser(email);
        var files = currentUser.hasGlobalProjectAccess()
                ? storedFileRepository.findAll(pageable)
                : storedFileRepository.findAllByOwnerId(currentUser.getId(), pageable);

        return PagedResponse.from(files.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<StoredFileResponse> findAllForUser(String email) {
        AppUser currentUser = getCurrentUser(email);
        List<StoredFile> files = currentUser.hasGlobalProjectAccess()
                ? storedFileRepository.findAllByOrderByUpdatedAtDesc()
                : storedFileRepository.findAllByOwnerIdOrderByUpdatedAtDesc(currentUser.getId());

        return files.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InitiateFileUploadResponse initiateUpload(InitiateFileUploadRequest request, String email) {
        AppUser currentUser = getCurrentUser(email);
        String originalFilename = normalizeOriginalFilename(request.originalFilename());
        String contentType = normalizeContentType(request.contentType());
        long declaredSize = request.sizeBytes();

        validateContentType(contentType);
        validateFileSize(declaredSize);
        ensureBucketExists();

        StoredFile storedFile = new StoredFile();
        storedFile.setOwner(currentUser);
        storedFile.setOriginalFilename(originalFilename);
        storedFile.setContentType(contentType);
        storedFile.setBucketName(appProperties.getStorage().getBucket());
        storedFile.setObjectKey(generateObjectKey(currentUser.getId(), originalFilename));
        storedFile.setStatus(StoredFileStatus.PENDING);

        StoredFile savedFile = storedFileRepository.save(storedFile);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(appProperties.getStorage().getPresignedExpiryMinutes());

        return new InitiateFileUploadResponse(
                savedFile.getId(),
                generateUploadUrl(savedFile),
                expiresAt
        );
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public StoredFileResponse completeUpload(Long id, String email) {
        AppUser currentUser = getCurrentUser(email);
        StoredFile storedFile = getAccessibleFile(id, currentUser);
        StatObjectResponse stat = statObject(storedFile);
        String resolvedContentType = StringUtils.hasText(stat.contentType())
                ? normalizeContentType(stat.contentType())
                : storedFile.getContentType();

        try {
            validateFileSize(stat.size());
            validateContentType(resolvedContentType);
        } catch (IllegalArgumentException exception) {
            deleteObject(storedFile);
            storedFileRepository.delete(storedFile);
            throw exception;
        }

        storedFile.setSizeBytes(stat.size());
        storedFile.setEtag(stat.etag());
        storedFile.setContentType(resolvedContentType);
        storedFile.setStatus(StoredFileStatus.READY);
        storedFile.setUploadedAt(LocalDateTime.now());

        return toResponse(storedFile);
    }

    @Transactional(readOnly = true)
    public PresignedDownloadResponse createDownloadUrl(Long id, String email) {
        AppUser currentUser = getCurrentUser(email);
        StoredFile storedFile = getAccessibleFile(id, currentUser);

        if (storedFile.getStatus() != StoredFileStatus.READY) {
            throw new IllegalArgumentException("The file upload is not completed yet");
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(appProperties.getStorage().getPresignedExpiryMinutes());

        return new PresignedDownloadResponse(
                storedFile.getOriginalFilename(),
                generateDownloadUrl(storedFile),
                expiresAt
        );
    }

    @Transactional
    public void delete(Long id, String email) {
        AppUser currentUser = getCurrentUser(email);
        StoredFile storedFile = getAccessibleFile(id, currentUser);

        deleteObject(storedFile);
        storedFileRepository.delete(storedFile);
    }

    private void ensureBucketExists() {
        String bucket = appProperties.getStorage().getBucket();

        try {
            boolean exists = internalMinioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!exists) {
                internalMinioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if (!"BucketAlreadyOwnedByYou".equals(code) && !"BucketAlreadyExists".equals(code)) {
                throw new StorageOperationException("Object storage is unavailable", exception);
            }
        } catch (Exception exception) {
            throw new StorageOperationException("Object storage is unavailable", exception);
        }
    }

    private String generateUploadUrl(StoredFile storedFile) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(storedFile.getBucketName())
                            .object(storedFile.getObjectKey())
                            .expiry(appProperties.getStorage().getPresignedExpiryMinutes(), TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageOperationException("Failed to generate an upload URL", exception);
        }
    }

    private String generateDownloadUrl(StoredFile storedFile) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(storedFile.getBucketName())
                            .object(storedFile.getObjectKey())
                            .expiry(appProperties.getStorage().getPresignedExpiryMinutes(), TimeUnit.MINUTES)
                            .extraQueryParams(buildDownloadQueryParams(storedFile))
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageOperationException("Failed to generate a download URL", exception);
        }
    }

    private Map<String, String> buildDownloadQueryParams(StoredFile storedFile) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put(
                "response-content-disposition",
                "attachment; filename=\"" + escapeHeaderValue(storedFile.getOriginalFilename()) + "\""
        );
        queryParams.put("response-content-type", storedFile.getContentType());
        return queryParams;
    }

    private StatObjectResponse statObject(StoredFile storedFile) {
        try {
            return internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(storedFile.getBucketName())
                            .object(storedFile.getObjectKey())
                            .build()
            );
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                throw new IllegalArgumentException("The uploaded object was not found in storage");
            }
            throw new StorageOperationException("Failed to verify the uploaded file", exception);
        } catch (Exception exception) {
            throw new StorageOperationException("Failed to verify the uploaded file", exception);
        }
    }

    private void deleteObject(StoredFile storedFile) {
        try {
            internalMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storedFile.getBucketName())
                            .object(storedFile.getObjectKey())
                            .build()
            );
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return;
            }
            throw new StorageOperationException("Failed to delete the object from storage", exception);
        } catch (Exception exception) {
            throw new StorageOperationException("Failed to delete the object from storage", exception);
        }
    }

    private void validateFileSize(long sizeBytes) {
        long maxFileSizeBytes = appProperties.getStorage().getMaxFileSizeBytes();

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("The file must not be empty");
        }

        if (sizeBytes > maxFileSizeBytes) {
            throw new IllegalArgumentException("The file exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes");
        }
    }

    private void validateContentType(String contentType) {
        List<String> allowedContentTypes = appProperties.getStorage().getAllowedContentTypes();

        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            return;
        }

        boolean allowed = allowedContentTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(allowedType -> allowedContentType(allowedType, contentType));

        if (!allowed) {
            throw new IllegalArgumentException("The provided content type is not allowed");
        }
    }

    private boolean allowedContentType(String allowedType, String contentType) {
        if (allowedType.endsWith("/*")) {
            String prefix = allowedType.substring(0, allowedType.length() - 1);
            return contentType.startsWith(prefix);
        }

        return allowedType.equalsIgnoreCase(contentType);
    }

    private StoredFile getAccessibleFile(Long id, AppUser currentUser) {
        StoredFile storedFile = storedFileRepository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException("File not found"));

        if (!currentUser.hasGlobalProjectAccess() && !storedFile.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You cannot access this file");
        }

        return storedFile;
    }

    private AppUser getCurrentUser(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private StoredFileResponse toResponse(StoredFile storedFile) {
        AppUser owner = storedFile.getOwner();

        return new StoredFileResponse(
                storedFile.getId(),
                storedFile.getOriginalFilename(),
                storedFile.getContentType(),
                storedFile.getSizeBytes(),
                storedFile.getStatus(),
                owner.getId(),
                owner.getFirstName() + " " + owner.getLastName(),
                owner.getEmail(),
                storedFile.getUploadedAt(),
                storedFile.getCreatedAt(),
                storedFile.getUpdatedAt()
        );
    }

    private String normalizeOriginalFilename(String originalFilename) {
        String candidate = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename.trim());

        if (!StringUtils.hasText(candidate)) {
            throw new IllegalArgumentException("The file name is required");
        }

        if (candidate.contains("..")) {
            throw new IllegalArgumentException("The file name is invalid");
        }

        int lastSlash = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        String cleaned = lastSlash >= 0 ? candidate.substring(lastSlash + 1) : candidate;

        if (!StringUtils.hasText(cleaned) || cleaned.length() > 255) {
            throw new IllegalArgumentException("The file name is invalid");
        }

        return cleaned;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType)
                ? contentType.trim()
                : "application/octet-stream";
    }

    private String generateObjectKey(Long ownerId, String originalFilename) {
        String sanitizedFilename = originalFilename
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-");

        if (!StringUtils.hasText(sanitizedFilename)) {
            sanitizedFilename = "file";
        }

        String dateSegment = LocalDate.now().format(KEY_DATE_FORMATTER);
        return "users/" + ownerId + "/" + dateSegment + "/" + UUID.randomUUID() + "-" + sanitizedFilename;
    }

    private String escapeHeaderValue(String value) {
        return value
                .replace("\\", "_")
                .replace("\"", "'")
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
