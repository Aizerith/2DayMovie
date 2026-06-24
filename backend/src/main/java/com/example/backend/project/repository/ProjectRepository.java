package com.example.backend.project.repository;

import com.example.backend.project.entity.Project;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    List<Project> findAll();

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Project> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "owner")
    java.util.Optional<Project> findById(Long id);

    @EntityGraph(attributePaths = "owner")
    List<Project> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    Page<Project> findAllByOwnerId(Long ownerId, Pageable pageable);
}
