package com.example.backend.project.service;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.project.dto.CreateProjectRequest;
import com.example.backend.project.dto.ProjectResponse;
import com.example.backend.project.dto.UpdateProjectRequest;
import com.example.backend.project.entity.Project;
import com.example.backend.project.exception.ProjectNotFoundException;
import com.example.backend.project.repository.ProjectRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public PagedResponse<ProjectResponse> findPageForUser(String email, Pageable pageable) {
        AppUser currentUser = getCurrentUser(email);
        var projects = currentUser.hasGlobalProjectAccess()
                ? projectRepository.findAll(pageable)
                : projectRepository.findAllByOwnerId(currentUser.getId(), pageable);

        return PagedResponse.from(projects.map(this::toResponse));
    }

    public List<ProjectResponse> findAllForUser(String email) {
        AppUser currentUser = getCurrentUser(email);
        List<Project> projects = currentUser.hasGlobalProjectAccess()
                ? projectRepository.findAll().stream()
                        .sorted(Comparator.comparing(Project::getUpdatedAt).reversed())
                        .toList()
                : projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(currentUser.getId());

        return projects.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, String email) {
        AppUser currentUser = getCurrentUser(email);

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        project.setStatus(request.status());
        project.setOwner(currentUser);

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request, String email) {
        AppUser currentUser = getCurrentUser(email);
        Project project = getManageableProject(id, currentUser);

        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        project.setStatus(request.status());

        return toResponse(project);
    }

    @Transactional
    public void delete(Long id, String email) {
        AppUser currentUser = getCurrentUser(email);
        Project project = getManageableProject(id, currentUser);
        projectRepository.delete(project);
    }

    private Project getManageableProject(Long id, AppUser currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        if (!currentUser.hasGlobalProjectAccess() && !project.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You cannot manage this project");
        }

        return project;
    }

    private AppUser getCurrentUser(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private ProjectResponse toResponse(Project project) {
        AppUser owner = project.getOwner();

        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                owner.getId(),
                owner.getFirstName() + " " + owner.getLastName(),
                owner.getEmail(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
