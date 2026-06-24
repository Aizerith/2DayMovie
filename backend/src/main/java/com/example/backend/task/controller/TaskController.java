package com.example.backend.task.controller;

import com.example.backend.common.dto.PagedResponse;
import com.example.backend.task.dto.CreateTaskRequest;
import com.example.backend.task.dto.TaskResponse;
import com.example.backend.task.dto.UpdateTaskRequest;
import com.example.backend.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "CRUD metier de reference pour les taches, avec assignation optionnelle")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Lister les taches visibles", description = "Retourne les taches visibles selon le role, les projets accessibles et les eventuelles assignations.")
    @ApiResponse(responseCode = "200", description = "Liste des taches retournee")
    public PagedResponse<TaskResponse> findAll(
            Authentication authentication,
            @Parameter(description = "Filtre optionnel par projet")
            @RequestParam(required = false) Long projectId,
            @PageableDefault(size = 20, sort = "updatedAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        return taskService.findPageForUser(authentication.getName(), projectId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer une tache", description = "Cree une tache rattachee a un projet et peut l assigner a un utilisateur actif.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tache creee"),
            @ApiResponse(responseCode = "400", description = "Payload invalide"),
            @ApiResponse(responseCode = "403", description = "Acces refuse")
    })
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request, Authentication authentication) {
        return taskService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour une tache", description = "Met a jour une tache existante, y compris son projet, son statut ou son assignation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tache mise a jour"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Tache introuvable")
    })
    public TaskResponse update(
            @Parameter(description = "Identifiant de la tache") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            Authentication authentication
    ) {
        return taskService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une tache", description = "Supprime une tache si l utilisateur courant dispose des droits de gestion.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tache supprimee"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Tache introuvable")
    })
    public void delete(@PathVariable Long id, Authentication authentication) {
        taskService.delete(id, authentication.getName());
    }
}
