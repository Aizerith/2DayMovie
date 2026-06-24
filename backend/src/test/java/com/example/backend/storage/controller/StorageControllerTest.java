package com.example.backend.storage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.security.JwtAuthenticationFilter;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.common.exception.ApiExceptionHandler;
import com.example.backend.storage.dto.InitiateFileUploadRequest;
import com.example.backend.storage.dto.InitiateFileUploadResponse;
import com.example.backend.storage.dto.PresignedDownloadResponse;
import com.example.backend.storage.dto.StoredFileResponse;
import com.example.backend.storage.entity.StoredFileStatus;
import com.example.backend.storage.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StorageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class StorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void findAllReturnsVisibleFiles() throws Exception {
        when(storageService.findPageForUser(eq("user@local.dev"), any(Pageable.class))).thenReturn(new PagedResponse<>(
                List.of(storedFileResponse(1L, "guide.pdf", StoredFileStatus.READY)),
                0,
                20,
                1,
                1
        ));

        mockMvc.perform(get("/api/files")
                        .principal(new TestingAuthenticationToken("user@local.dev", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originalFilename").value("guide.pdf"))
                .andExpect(jsonPath("$.items[0].status").value("READY"));
    }

    @Test
    void initiateUploadReturnsCreatedResponse() throws Exception {
        InitiateFileUploadRequest request = new InitiateFileUploadRequest("guide.pdf", "application/pdf", 2048L);

        when(storageService.initiateUpload(request, "user@local.dev")).thenReturn(
                new InitiateFileUploadResponse(2L, "http://localhost:9000/upload", LocalDateTime.now().plusMinutes(15))
        );

        mockMvc.perform(post("/api/files/uploads/presign")
                        .principal(new TestingAuthenticationToken("user@local.dev", null))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value(2))
                .andExpect(jsonPath("$.uploadUrl").value("http://localhost:9000/upload"));
    }

    @Test
    void completeUploadReturnsReadyFile() throws Exception {
        when(storageService.completeUpload(3L, "user@local.dev")).thenReturn(
                storedFileResponse(3L, "avatar.png", StoredFileStatus.READY)
        );

        mockMvc.perform(post("/api/files/3/complete")
                        .principal(new TestingAuthenticationToken("user@local.dev", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void createDownloadUrlReturnsPresignedResponse() throws Exception {
        when(storageService.createDownloadUrl(4L, "user@local.dev")).thenReturn(
                new PresignedDownloadResponse("avatar.png", "http://localhost:9000/download", LocalDateTime.now().plusMinutes(15))
        );

        mockMvc.perform(get("/api/files/4/download-url")
                        .principal(new TestingAuthenticationToken("user@local.dev", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("avatar.png"))
                .andExpect(jsonPath("$.downloadUrl").value("http://localhost:9000/download"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/files/5")
                        .principal(new TestingAuthenticationToken("user@local.dev", null)))
                .andExpect(status().isNoContent());

        verify(storageService).delete(5L, "user@local.dev");
    }

    @Test
    void initiateUploadRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/files/uploads/presign")
                        .principal(new TestingAuthenticationToken("user@local.dev", null))
                        .contentType(APPLICATION_JSON)
                        .content("{\"originalFilename\":\"\",\"contentType\":\"application/pdf\",\"sizeBytes\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    private StoredFileResponse storedFileResponse(Long id, String fileName, StoredFileStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new StoredFileResponse(
                id,
                fileName,
                "application/pdf",
                2048L,
                status,
                1L,
                "Admin Local",
                "admin@local.dev",
                now,
                now.minusDays(1),
                now
        );
    }
}
