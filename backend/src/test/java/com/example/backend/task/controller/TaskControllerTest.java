package com.example.backend.task.controller;

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
import com.example.backend.task.dto.CreateTaskRequest;
import com.example.backend.task.dto.TaskResponse;
import com.example.backend.task.dto.UpdateTaskRequest;
import com.example.backend.task.entity.TaskPriority;
import com.example.backend.task.entity.TaskStatus;
import com.example.backend.task.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void findAllReturnsTasksForCurrentUser() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        when(taskService.findPageForUser(eq("user@local.dev"), eq(10L), any(Pageable.class))).thenReturn(new PagedResponse<>(
                List.of(new TaskResponse(1L, 10L, "Portal", 2L, "Jane Doe", 3L, "John Smith", "john@local.dev", "Build page", "Implement CRUD", TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), true, LocalDateTime.now(), LocalDateTime.now())),
                0,
                20,
                1,
                1
        ));

        mockMvc.perform(get("/api/tasks")
                        .principal(auth)
                .param("projectId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].projectName").value("Portal"));
    }

    @Test
    void createReturnsCreatedTask() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        CreateTaskRequest request = new CreateTaskRequest(10L, 3L, "Build page", "Implement CRUD", TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now());
        when(taskService.create(request, "user@local.dev")).thenReturn(
                new TaskResponse(1L, 10L, "Portal", 2L, "Jane Doe", 3L, "John Smith", "john@local.dev", "Build page", "Implement CRUD", TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now(), true, LocalDateTime.now(), LocalDateTime.now())
        );

        mockMvc.perform(post("/api/tasks")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 10,
                                  "assigneeId": 3,
                                  "title": "Build page",
                                  "description": "Implement CRUD",
                                  "status": "TODO",
                                  "priority": "HIGH",
                                  "dueDate": "%s"
                                }
                                """.formatted(LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void updateReturnsUpdatedTask() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());
        UpdateTaskRequest request = new UpdateTaskRequest(10L, 3L, "Build page", "Done", TaskStatus.DONE, TaskPriority.MEDIUM, null);
        when(taskService.update(1L, request, "user@local.dev")).thenReturn(
                new TaskResponse(1L, 10L, "Portal", 2L, "Jane Doe", 3L, "John Smith", "john@local.dev", "Build page", "Done", TaskStatus.DONE, TaskPriority.MEDIUM, null, true, LocalDateTime.now(), LocalDateTime.now())
        );

        mockMvc.perform(put("/api/tasks/1")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());

        mockMvc.perform(delete("/api/tasks/1").principal(auth))
                .andExpect(status().isNoContent());

        verify(taskService).delete(1L, "user@local.dev");
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user@local.dev", null, List.of());

        mockMvc.perform(post("/api/tasks")
                        .principal(auth)
                        .contentType(APPLICATION_JSON)
                        .content("{\"projectId\":null,\"title\":\"\",\"description\":\"\",\"status\":null,\"priority\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
