package com.example.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.example.backend.user.dto.AssignableUserResponse;
import com.example.backend.user.dto.CreateUserRequest;
import com.example.backend.user.dto.UpdateUserRequest;
import com.example.backend.user.dto.UserAdminResponse;
import com.example.backend.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void findAllReturnsUsers() throws Exception {
        when(userService.findPage(any(Pageable.class))).thenReturn(new PagedResponse<>(
                List.of(new UserAdminResponse(1L, "admin@local.dev", "Admin", "Local", "ADMIN", true, true)),
                0,
                20,
                1,
                1
        ));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].email").value("admin@local.dev"));
    }

    @Test
    void findAssignableReturnsEnabledUsers() throws Exception {
        when(userService.findAssignableUsers()).thenReturn(List.of(
                new AssignableUserResponse(2L, "manager@local.dev", "Manager Local", "MANAGER")
        ));

        mockMvc.perform(get("/api/users/assignable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("MANAGER"));
    }

    @Test
    void createReturnsCreatedUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest("user@local.dev", "Jane", "Doe", "Secret123!", "USER", true);
        UserAdminResponse response = new UserAdminResponse(2L, "user@local.dev", "Jane", "Doe", "USER", true, false);

        when(userService.create(request)).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void updateReturnsUpdatedUser() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("user@local.dev", "Jane", "Doe", "", "ADMIN", true);
        UserAdminResponse response = new UserAdminResponse(2L, "user@local.dev", "Jane", "Doe", "ADMIN", true, false);

        when(userService.update(2L, request)).thenReturn(response);

        mockMvc.perform(put("/api/users/2")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/2"))
                .andExpect(status().isNoContent());

        verify(userService).delete(2L);
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"\",\"firstName\":\"\",\"lastName\":\"\",\"password\":\"\",\"role\":\"\",\"enabled\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
