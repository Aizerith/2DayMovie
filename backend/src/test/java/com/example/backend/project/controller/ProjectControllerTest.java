package com.example.backend.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.security.JwtAuthenticationFilter;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.common.exception.ApiExceptionHandler;
import com.example.backend.project.dto.CreateProjectRequest;
import com.example.backend.project.dto.ProjectResponse;
import com.example.backend.project.dto.UpdateProjectRequest;
import com.example.backend.project.entity.ProjectStatus;
import com.example.backend.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void findAllReturnsProjectsForCurrentUser() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        when(projectService.findPageForUser(eq("user@local.dev"), any(Pageable.class))).thenReturn(new PagedResponse<>(
                List.of(
                new ProjectResponse(1L, "Portal", "Workspace", ProjectStatus.ACTIVE, 2L, "Jane Doe", "user@local.dev", LocalDateTime.now(), LocalDateTime.now())
                ),
                0,
                20,
                1,
                1
        ));

        mockMvc.perform(get("/api/projects").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Portal"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void createReturnsCreatedProject() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        CreateProjectRequest request = new CreateProjectRequest("Portal", "Workspace", ProjectStatus.DRAFT);
        when(projectService.create(request, "user@local.dev")).thenReturn(
                new ProjectResponse(1L, "Portal", "Workspace", ProjectStatus.DRAFT, 2L, "Jane Doe", "user@local.dev", LocalDateTime.now(), LocalDateTime.now())
        );

        mockMvc.perform(post("/api/projects")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void updateReturnsUpdatedProject() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        UpdateProjectRequest request = new UpdateProjectRequest("Portal", "Updated", ProjectStatus.ARCHIVED);
        when(projectService.update(1L, request, "user@local.dev")).thenReturn(
                new ProjectResponse(1L, "Portal", "Updated", ProjectStatus.ARCHIVED, 2L, "Jane Doe", "user@local.dev", LocalDateTime.now(), LocalDateTime.now())
        );

        mockMvc.perform(put("/api/projects/1")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());

        mockMvc.perform(delete("/api/projects/1").principal(auth))
                .andExpect(status().isNoContent());

        verify(projectService).delete(1L, "user@local.dev");
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());

        mockMvc.perform(post("/api/projects")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\",\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
