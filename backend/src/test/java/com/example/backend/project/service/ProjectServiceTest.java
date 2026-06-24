package com.example.backend.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.project.dto.CreateProjectRequest;
import com.example.backend.project.dto.UpdateProjectRequest;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectStatus;
import com.example.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private ProjectService projectService;

    private AppUser adminUser;
    private AppUser managerUser;
    private AppUser standardUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        adminUser = buildUser(1L, "admin@local.dev", "ADMIN");
        managerUser = buildUser(4L, "manager@local.dev", "MANAGER");
        standardUser = buildUser(2L, "user@local.dev", "USER");
        otherUser = buildUser(3L, "other@local.dev", "USER");
    }

    @Test
    void findAllForAdminReturnsAllProjectsSortedByUpdatedAt() {
        Project olderProject = buildProject(10L, "Legacy", adminUser, LocalDateTime.now().minusDays(1));
        Project recentProject = buildProject(11L, "Portal", standardUser, LocalDateTime.now());

        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(adminUser));
        when(projectRepository.findAll()).thenReturn(List.of(olderProject, recentProject));

        var response = projectService.findAllForUser("admin@local.dev");

        assertEquals(2, response.size());
        assertEquals("Portal", response.getFirst().name());
        assertEquals("Legacy", response.getLast().name());
    }

    @Test
    void findAllForManagerReturnsAllProjectsSortedByUpdatedAt() {
        Project olderProject = buildProject(12L, "Legacy", adminUser, LocalDateTime.now().minusDays(1));
        Project recentProject = buildProject(13L, "Portal", standardUser, LocalDateTime.now());

        when(appUserRepository.findByEmailIgnoreCase("manager@local.dev")).thenReturn(Optional.of(managerUser));
        when(projectRepository.findAll()).thenReturn(List.of(olderProject, recentProject));

        var response = projectService.findAllForUser("manager@local.dev");

        assertEquals(2, response.size());
        assertEquals("Portal", response.getFirst().name());
    }

    @Test
    void findAllForStandardUserOnlyReturnsOwnedProjects() {
        Project ownedProject = buildProject(20L, "Owned", standardUser, LocalDateTime.now());

        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of(ownedProject));

        var response = projectService.findAllForUser("user@local.dev");

        assertEquals(1, response.size());
        assertEquals("Owned", response.getFirst().name());
        assertEquals("user@local.dev", response.getFirst().ownerEmail());
    }

    @Test
    void createAssignsCurrentUserAsOwner() {
        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project savedProject = invocation.getArgument(0);
            savedProject.setId(30L);
            savedProject.setCreatedAt(LocalDateTime.now());
            savedProject.setUpdatedAt(LocalDateTime.now());
            return savedProject;
        });

        var response = projectService.create(
                new CreateProjectRequest("  Client Portal  ", "  Main workspace  ", ProjectStatus.ACTIVE),
                "user@local.dev"
        );

        assertEquals("Client Portal", response.name());
        assertEquals("Main workspace", response.description());
        assertEquals(2L, response.ownerId());
    }

    @Test
    void updateRejectsProjectManagedByAnotherUser() {
        Project foreignProject = buildProject(40L, "Shared", otherUser, LocalDateTime.now());

        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(standardUser));
        when(projectRepository.findById(40L)).thenReturn(Optional.of(foreignProject));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> projectService.update(
                        40L,
                        new UpdateProjectRequest("Renamed", "Updated description", ProjectStatus.ARCHIVED),
                        "user@local.dev"
                )
        );

        assertEquals("You cannot manage this project", exception.getMessage());
    }

    @Test
    void deleteAllowsAdminToRemoveAnyProject() {
        Project managedProject = buildProject(50L, "Admin Target", standardUser, LocalDateTime.now());

        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(adminUser));
        when(projectRepository.findById(50L)).thenReturn(Optional.of(managedProject));

        projectService.delete(50L, "admin@local.dev");

        verify(projectRepository).delete(managedProject);
    }

    private AppUser buildUser(Long id, String email, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName(email.split("@")[0]);
        user.setLastName("User");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private Project buildProject(Long id, String name, AppUser owner, LocalDateTime updatedAt) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setDescription(name + " description");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setOwner(owner);
        project.setCreatedAt(updatedAt.minusDays(1));
        project.setUpdatedAt(updatedAt);
        return project;
    }
}
