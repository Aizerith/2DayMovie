package com.example.backend.project.controller;

import com.example.backend.common.dto.PagedResponse;
import com.example.backend.project.dto.CreateProjectRequest;
import com.example.backend.project.dto.ProjectResponse;
import com.example.backend.project.dto.UpdateProjectRequest;
import com.example.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "CRUD metier de reference pour les projets")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "Lister les projets visibles", description = "Retourne tous les projets visibles pour l utilisateur courant selon son role.")
    @ApiResponse(responseCode = "200", description = "Liste des projets retournee")
    public PagedResponse<ProjectResponse> findAll(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "updatedAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        return projectService.findPageForUser(authentication.getName(), pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un projet", description = "Cree un nouveau projet rattache a l utilisateur courant, sauf pour les roles a portee globale.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Projet cree"),
            @ApiResponse(responseCode = "400", description = "Payload invalide")
    })
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        return projectService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour un projet", description = "Met a jour un projet existant si l utilisateur courant a les permissions necessaires.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Projet mis a jour"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable")
    })
    public ProjectResponse update(
            @Parameter(description = "Identifiant du projet") @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication authentication
    ) {
        return projectService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un projet", description = "Supprime un projet si l utilisateur courant a les droits de gestion sur celui-ci.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Projet supprime"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable")
    })
    public void delete(@PathVariable Long id, Authentication authentication) {
        projectService.delete(id, authentication.getName());
    }
}
