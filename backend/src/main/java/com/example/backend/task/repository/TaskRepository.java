package com.example.backend.task.repository;

import com.example.backend.task.entity.Task;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Override
    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    List<Task> findAll();

    @Override
    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    Page<Task> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    java.util.Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    List<Task> findAllByProjectOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    Page<Task> findAllByProjectOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    List<Task> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    Page<Task> findAllByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    List<Task> findAllByProjectOwnerIdAndProjectIdOrderByUpdatedAtDesc(Long ownerId, Long projectId);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    List<Task> findAllByProjectOwnerIdOrAssigneeIdOrderByUpdatedAtDesc(Long ownerId, Long assigneeId);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    Page<Task> findAllByProjectOwnerIdOrAssigneeId(Long ownerId, Long assigneeId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee"})
    Page<Task> findAllByProjectIdAndProjectOwnerIdOrProjectIdAndAssigneeId(
            Long ownedProjectId,
            Long ownerId,
            Long assignedProjectId,
            Long assigneeId,
            Pageable pageable
    );
}
