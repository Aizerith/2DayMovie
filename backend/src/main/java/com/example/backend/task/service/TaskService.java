package com.example.backend.task.service;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.common.realtime.RealtimeNotificationService;
import com.example.backend.project.entity.Project;
import com.example.backend.project.exception.ProjectNotFoundException;
import com.example.backend.project.repository.ProjectRepository;
import com.example.backend.user.exception.UserNotFoundException;
import com.example.backend.task.dto.CreateTaskRequest;
import com.example.backend.task.dto.TaskResponse;
import com.example.backend.task.dto.UpdateTaskRequest;
import com.example.backend.task.entity.Task;
import com.example.backend.task.exception.TaskNotFoundException;
import com.example.backend.task.repository.TaskRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;
    private final RealtimeNotificationService realtimeNotificationService;

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> findPageForUser(String email, Long projectId, Pageable pageable) {
        AppUser currentUser = getCurrentUser(email);
        var tasks = currentUser.hasGlobalProjectAccess()
                ? findGlobalTaskPage(projectId, pageable)
                : findScopedTaskPage(currentUser, projectId, pageable);

        return PagedResponse.from(tasks.map(task -> toResponse(task, currentUser)));
    }

    public List<TaskResponse> findAllForUser(String email, Long projectId) {
        AppUser currentUser = getCurrentUser(email);
        List<Task> tasks;

        if (currentUser.hasGlobalProjectAccess()) {
            tasks = projectId != null
                    ? taskRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId)
                    : taskRepository.findAll().stream()
                            .sorted(Comparator.comparing(Task::getUpdatedAt).reversed())
                            .toList();
        } else if (projectId != null) {
            tasks = taskRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                    .filter(task -> canViewTask(task, currentUser))
                    .toList();
        } else {
            tasks = taskRepository.findAllByProjectOwnerIdOrAssigneeIdOrderByUpdatedAtDesc(currentUser.getId(), currentUser.getId());
        }

        return tasks.stream().map(task -> toResponse(task, currentUser)).toList();
    }

    private org.springframework.data.domain.Page<Task> findGlobalTaskPage(Long projectId, Pageable pageable) {
        return projectId != null
                ? taskRepository.findAllByProjectId(projectId, pageable)
                : taskRepository.findAll(pageable);
    }

    private org.springframework.data.domain.Page<Task> findScopedTaskPage(AppUser currentUser, Long projectId, Pageable pageable) {
        if (projectId != null) {
            return taskRepository.findAllByProjectIdAndProjectOwnerIdOrProjectIdAndAssigneeId(
                    projectId,
                    currentUser.getId(),
                    projectId,
                    currentUser.getId(),
                    pageable
            );
        }

        return taskRepository.findAllByProjectOwnerIdOrAssigneeId(currentUser.getId(), currentUser.getId(), pageable);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request, String email) {
        AppUser currentUser = getCurrentUser(email);
        Project project = getManageableProject(request.projectId(), currentUser);

        Task task = new Task();
        task.setProject(project);
        task.setAssignee(resolveAssignee(request.assigneeId()));
        task.setTitle(request.title().trim());
        task.setDescription(request.description().trim());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());

        Task savedTask = taskRepository.save(task);
        TaskResponse response = toResponse(savedTask, currentUser);
        notifyTaskWatchers(savedTask, currentUser, "TASK_CREATED", "Task created: " + savedTask.getTitle());
        return response;
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request, String email) {
        AppUser currentUser = getCurrentUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        ensureTaskManageable(task, currentUser);
        Project project = getManageableProject(request.projectId(), currentUser);

        task.setProject(project);
        task.setAssignee(resolveAssignee(request.assigneeId()));
        task.setTitle(request.title().trim());
        task.setDescription(request.description().trim());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());

        TaskResponse response = toResponse(task, currentUser);
        notifyTaskWatchers(task, currentUser, "TASK_UPDATED", "Task updated: " + task.getTitle());
        return response;
    }

    @Transactional
    public void delete(Long id, String email) {
        AppUser currentUser = getCurrentUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        ensureTaskManageable(task, currentUser);
        notifyTaskWatchers(task, currentUser, "TASK_DELETED", "Task deleted: " + task.getTitle());
        taskRepository.delete(task);
    }

    private void notifyTaskWatchers(Task task, AppUser currentUser, String type, String message) {
        Set<String> recipients = new HashSet<>();
        recipients.add(currentUser.getEmail());
        recipients.add(task.getProject().getOwner().getEmail());

        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee().getEmail());
        }

        Map<String, Object> payload = Map.of(
                "taskId", task.getId(),
                "projectId", task.getProject().getId()
        );

        runAfterCommit(() -> recipients.forEach(email -> realtimeNotificationService.notifyUser(email, type, message, payload)));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void ensureTaskManageable(Task task, AppUser currentUser) {
        if (!canManageTask(task, currentUser)) {
            throw new AccessDeniedException("You cannot manage this task");
        }
    }

    private Project getManageableProject(Long projectId, AppUser currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        if (!currentUser.hasGlobalProjectAccess() && !project.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You cannot attach a task to this project");
        }

        return project;
    }

    private AppUser resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }

        AppUser assignee = appUserRepository.findById(assigneeId)
                .orElseThrow(() -> new UserNotFoundException("Assignee not found"));

        if (!assignee.isEnabled()) {
            throw new AccessDeniedException("You cannot assign a task to a disabled user");
        }

        return assignee;
    }

    private AppUser getCurrentUser(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private boolean canViewTask(Task task, AppUser currentUser) {
        return currentUser.hasGlobalProjectAccess()
                || task.getProject().getOwner().getId().equals(currentUser.getId())
                || (task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getId()));
    }

    private boolean canManageTask(Task task, AppUser currentUser) {
        return currentUser.hasGlobalProjectAccess()
                || task.getProject().getOwner().getId().equals(currentUser.getId());
    }

    private TaskResponse toResponse(Task task, AppUser currentUser) {
        Project project = task.getProject();
        AppUser assignee = task.getAssignee();

        return new TaskResponse(
                task.getId(),
                project.getId(),
                project.getName(),
                project.getOwner().getId(),
                project.getOwner().getFirstName() + " " + project.getOwner().getLastName(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : null,
                assignee != null ? assignee.getEmail() : null,
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                canManageTask(task, currentUser),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
