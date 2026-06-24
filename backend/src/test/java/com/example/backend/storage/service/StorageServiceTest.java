package com.example.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.config.AppProperties;
import com.example.backend.storage.dto.InitiateFileUploadRequest;
import com.example.backend.storage.entity.StoredFile;
import com.example.backend.storage.entity.StoredFileStatus;
import com.example.backend.storage.repository.StoredFileRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private MinioClient internalMinioClient;

    @Mock
    private MinioClient publicMinioClient;

    private StorageService storageService;

    private AppProperties appProperties;
    private AppUser standardUser;
    private AppUser managerUser;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getStorage().setBucket("boilerplate-files");
        appProperties.getStorage().setEndpoint("http://minio:9000");
        appProperties.getStorage().setPublicEndpoint("http://localhost:9000");
        appProperties.getStorage().setAccessKey("minioadmin");
        appProperties.getStorage().setSecretKey("minioadmin");
        appProperties.getStorage().setPresignedExpiryMinutes(15);
        appProperties.getStorage().setMaxFileSizeBytes(5_000L);
        appProperties.getStorage().setAllowedContentTypes(List.of("image/*", "application/pdf", "text/*"));

        storageService = new StorageService(storedFileRepository, appUserRepository, appProperties, internalMinioClient, publicMinioClient);

        standardUser = buildUser(7L, "user@local.dev", "USER");
        managerUser = buildUser(8L, "manager@local.dev", "MANAGER");
    }

    @Test
    void initiateUploadCreatesPendingFileAndReturnsPresignedUrl() throws Exception {
        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(internalMinioClient.bucketExists(any())).thenReturn(true);
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/boilerplate-files/upload");
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(11L);
            storedFile.setCreatedAt(LocalDateTime.now());
            storedFile.setUpdatedAt(LocalDateTime.now());
            return storedFile;
        });

        var response = storageService.initiateUpload(
                new InitiateFileUploadRequest(" avatar.png ", "image/png", 2048L),
                "user@local.dev"
        );

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(captor.capture());

        StoredFile savedFile = captor.getValue();
        assertEquals(11L, response.fileId());
        assertEquals("http://localhost:9000/boilerplate-files/upload", response.uploadUrl());
        assertEquals("avatar.png", savedFile.getOriginalFilename());
        assertEquals("image/png", savedFile.getContentType());
        assertEquals("boilerplate-files", savedFile.getBucketName());
        assertEquals(StoredFileStatus.PENDING, savedFile.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(savedFile.getObjectKey().startsWith("users/7/"));
    }

    @Test
    void findAllForManagerReturnsGlobalFileList() {
        StoredFile storedFile = buildStoredFile(40L, standardUser, StoredFileStatus.READY);

        when(appUserRepository.findByEmailIgnoreCase("manager@local.dev")).thenReturn(Optional.of(managerUser));
        when(storedFileRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(storedFile));

        var response = storageService.findAllForUser("manager@local.dev");

        assertEquals(1, response.size());
        assertEquals(40L, response.getFirst().id());
        assertEquals("Jane Doe", response.getFirst().ownerName());
    }

    @Test
    void completeUploadMarksFileReadyWithResolvedContentType() throws Exception {
        StoredFile storedFile = buildStoredFile(50L, standardUser, StoredFileStatus.PENDING);
        StatObjectResponse statObjectResponse = org.mockito.Mockito.mock(StatObjectResponse.class);

        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(storedFileRepository.findById(50L)).thenReturn(Optional.of(storedFile));
        when(internalMinioClient.statObject(any())).thenReturn(statObjectResponse);
        when(statObjectResponse.size()).thenReturn(2048L);
        when(statObjectResponse.etag()).thenReturn("etag-123");
        when(statObjectResponse.contentType()).thenReturn("image/png");

        var response = storageService.completeUpload(50L, "user@local.dev");

        assertEquals(StoredFileStatus.READY, storedFile.getStatus());
        assertEquals(2048L, storedFile.getSizeBytes());
        assertEquals("etag-123", storedFile.getEtag());
        assertEquals("image/png", storedFile.getContentType());
        assertNotNull(storedFile.getUploadedAt());
        assertEquals(StoredFileStatus.READY, response.status());
    }

    @Test
    void completeUploadDeletesObjectAndMetadataWhenUploadedFileIsTooLarge() throws Exception {
        appProperties.getStorage().setMaxFileSizeBytes(10L);
        StoredFile storedFile = buildStoredFile(60L, standardUser, StoredFileStatus.PENDING);
        StatObjectResponse statObjectResponse = org.mockito.Mockito.mock(StatObjectResponse.class);

        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(storedFileRepository.findById(60L)).thenReturn(Optional.of(storedFile));
        when(internalMinioClient.statObject(any())).thenReturn(statObjectResponse);
        when(statObjectResponse.size()).thenReturn(25L);
        when(statObjectResponse.contentType()).thenReturn("image/png");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.completeUpload(60L, "user@local.dev")
        );

        assertEquals("The file exceeds the maximum allowed size of 10 bytes", exception.getMessage());
        verify(internalMinioClient).removeObject(any(RemoveObjectArgs.class));
        verify(storedFileRepository).delete(storedFile);
    }

    @Test
    void createDownloadUrlRejectsFileOwnedByAnotherUser() {
        StoredFile storedFile = buildStoredFile(70L, managerUser, StoredFileStatus.READY);

        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(storedFileRepository.findById(70L)).thenReturn(Optional.of(storedFile));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> storageService.createDownloadUrl(70L, "user@local.dev")
        );

        assertEquals("You cannot access this file", exception.getMessage());
    }

    private AppUser buildUser(Long id, String email, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(role);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setPasswordHash("encoded-password");
        return user;
    }

    private StoredFile buildStoredFile(Long id, AppUser owner, StoredFileStatus status) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setOwner(owner);
        storedFile.setOriginalFilename("guide.pdf");
        storedFile.setContentType("application/pdf");
        storedFile.setBucketName("boilerplate-files");
        storedFile.setObjectKey("users/" + owner.getId() + "/2026/04/file-guide.pdf");
        storedFile.setStatus(status);
        storedFile.setCreatedAt(LocalDateTime.now().minusDays(1));
        storedFile.setUpdatedAt(LocalDateTime.now());
        return storedFile;
    }
}
