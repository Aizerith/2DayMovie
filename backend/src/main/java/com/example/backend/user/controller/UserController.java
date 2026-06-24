package com.example.backend.user.controller;

import com.example.backend.common.dto.PagedResponse;
import com.example.backend.user.dto.CreateUserRequest;
import com.example.backend.user.dto.UpdateUserRequest;
import com.example.backend.user.dto.AssignableUserResponse;
import com.example.backend.user.dto.UserAdminResponse;
import com.example.backend.user.service.UserService;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestion des utilisateurs et liste des utilisateurs assignables")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lister les utilisateurs", description = "Retourne la liste complete des utilisateurs pour l administration.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des utilisateurs retournee"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs")
    })
    public PagedResponse<UserAdminResponse> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        return userService.findPage(pageable);
    }

    @GetMapping("/assignable")
    @Operation(summary = "Lister les utilisateurs assignables", description = "Retourne les utilisateurs actifs qui peuvent etre proposes dans les formulaires d assignation.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Liste des utilisateurs assignables retournee")
    public List<AssignableUserResponse> findAssignableUsers() {
        return userService.findAssignableUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un utilisateur", description = "Cree un utilisateur avec email, role et mot de passe.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Utilisateur cree"),
            @ApiResponse(responseCode = "400", description = "Payload invalide"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise")
    })
    public UserAdminResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour un utilisateur", description = "Met a jour le profil, le role ou l etat d activation d un utilisateur.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur mis a jour"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise")
    })
    public UserAdminResponse update(@Parameter(description = "Identifiant de l utilisateur") @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un utilisateur", description = "Supprime un utilisateur existant.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Utilisateur supprime"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    })
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
