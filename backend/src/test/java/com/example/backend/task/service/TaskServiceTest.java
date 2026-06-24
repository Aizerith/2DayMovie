package com.example.backend.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.realtime.RealtimeNotificationService;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectStatus;
import com.example.backend.project.repository.ProjectRepository;
import com.example.backend.task.dto.CreateTaskRequest;
import com.example.backend.task.dto.UpdateTaskRequest;
import com.example.backend.task.entity.Task;
import com.example.backend.task.entity.TaskPriority;
import com.example.backend.task.entity.TaskStatus;
import com.example.backend.task.repository.TaskRepository;
import java.time.LocalDate;
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
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RealtimeNotificationService realtimeNotificationService;

    @InjectMocks
    private TaskService taskService;

    private AppUser adminUser;
    private AppUser managerUser;
    private AppUser ownerUser;
    private AppUser outsiderUser;
    private AppUser assigneeUser;
    private Project ownedProject;

    @BeforeEach
    void setUp() {
        adminUser = buildUser(1L, "admin@local.dev", "ADMIN");
        managerUser = buildUser(4L, "manager@local.dev", "MANAGER");
        ownerUser = buildUser(2L, "owner@local.dev", "USER");
        outsiderUser = buildUser(3L, "outsider@local.dev", "USER");
        assigneeUser = buildUser(5L, "assignee@local.dev", "USER");
        ownedProject = buildProject(10L, "Workspace", ownerUser);
    }

    @Test
    void findAllForUserWithProjectFilterUsesOwnerScopedQuery() {
        Task task = buildTask(100L, "Write docs", ownedProject);

        when(appUserRepository.findByEmailIgnoreCase("owner@local.dev")).thenReturn(Optional.of(ownerUser));
        when(taskRepository.findAllByProjectIdOrderByUpdatedAtDesc(10L)).thenReturn(List.of(task));

        var response = taskService.findAllForUser("owner@local.dev", 10L);

        assertEquals(1, response.size());
        assertEquals("Write docs", response.getFirst().title());
        assertEquals("Workspace", response.getFirst().projectName());
    }

    @Test
    void createRejectsTaskOnProjectOwnedByAnotherUser() {
        when(appUserRepository.findByEmailIgnoreCase("outsider@local.dev")).thenReturn(Optional.of(outsiderUser));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(ownedProject));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> taskService.create(
                        new CreateTaskRequest(10L, null, "Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM, null),
                        "outsider@local.dev"
                )
        );

        assertEquals("You cannot attach a task to this project", exception.getMessage());
    }

    @Test
    void createPersistsTaskForOwnedProject() {
        when(appUserRepository.findByEmailIgnoreCase("owner@local.dev")).thenReturn(Optional.of(ownerUser));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(ownedProject));
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(assigneeUser));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task savedTask = invocation.getArgument(0);
            savedTask.setId(101L);
            savedTask.setCreatedAt(LocalDateTime.now());
            savedTask.setUpdatedAt(LocalDateTime.now());
            return savedTask;
        });

        var response = taskService.create(
                new CreateTaskRequest(10L, 5L, "  Ship feature  ", "  Final validation  ", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, LocalDate.now()),
                "owner@local.dev"
        );

        assertEquals("Ship feature", response.title());
        assertEquals(TaskPriority.HIGH, response.priority());
        assertEquals(10L, response.projectId());
        assertEquals(5L, response.assigneeId());
    }

    @Test
    void updateRejectsTaskManagedByAnotherUser() {
        Task task = buildTask(102L, "Secure API", ownedProject);

        when(appUserRepository.findByEmailIgnoreCase("outsider@local.dev")).thenReturn(Optional.of(outsiderUser));
        when(taskRepository.findById(102L)).thenReturn(Optional.of(task));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> taskService.update(
                        102L,
                        new UpdateTaskRequest(10L, null, "Secure API", "Refine roles", TaskStatus.DONE, TaskPriority.MEDIUM, null),
                        "outsider@local.dev"
                )
        );

        assertEquals("You cannot manage this task", exception.getMessage());
    }

    @Test
    void deleteAllowsAdminToRemoveAnyTask() {
        Task task = buildTask(103L, "Review", ownedProject);

        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(adminUser));
        when(taskRepository.findById(103L)).thenReturn(Optional.of(task));

        taskService.delete(103L, "admin@local.dev");

        verify(taskRepository).delete(task);
    }

    @Test
    void findAllForAssigneeReturnsAssignedTaskEvenIfProjectIsNotOwned() {
        Task task = buildTask(120L, "Review release", ownedProject);
        task.setAssignee(assigneeUser);

        when(appUserRepository.findByEmailIgnoreCase("assignee@local.dev")).thenReturn(Optional.of(assigneeUser));
        when(taskRepository.findAllByProjectOwnerIdOrAssigneeIdOrderByUpdatedAtDesc(5L, 5L)).thenReturn(List.of(task));

        var response = taskService.findAllForUser("assignee@local.dev", null);

        assertEquals(1, response.size());
        assertEquals("Review release", response.getFirst().title());
        org.junit.jupiter.api.Assertions.assertFalse(response.getFirst().manageable());
        assertEquals(5L, response.getFirst().assigneeId());
    }

    @Test
    void deleteAllowsManagerToRemoveAnyTask() {
        Task task = buildTask(130L, "Review", ownedProject);

        when(appUserRepository.findByEmailIgnoreCase("manager@local.dev")).thenReturn(Optional.of(managerUser));
        when(taskRepository.findById(130L)).thenReturn(Optional.of(task));

        taskService.delete(130L, "manager@local.dev");

        verify(taskRepository).delete(task);
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

    private Project buildProject(Long id, String name, AppUser owner) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setDescription(name + " description");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setOwner(owner);
        project.setCreatedAt(LocalDateTime.now().minusDays(1));
        project.setUpdatedAt(LocalDateTime.now());
        return project;
    }

    private Task buildTask(Long id, String title, Project project) {
        Task task = new Task();
        task.setId(id);
        task.setProject(project);
        task.setTitle(title);
        task.setDescription(title + " description");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedAt(LocalDateTime.now().minusDays(1));
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
