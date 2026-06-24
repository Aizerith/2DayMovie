package com.example.backend.storage.controller;

import com.example.backend.common.dto.PagedResponse;
import com.example.backend.storage.dto.InitiateFileUploadRequest;
import com.example.backend.storage.dto.InitiateFileUploadResponse;
import com.example.backend.storage.dto.PresignedDownloadResponse;
import com.example.backend.storage.dto.StoredFileResponse;
import com.example.backend.storage.service.StorageService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Gestion de fichiers via MinIO prive et URLs presignees")
@SecurityRequirement(name = "bearerAuth")
public class StorageController {

    private final StorageService storageService;

    @GetMapping
    @Operation(summary = "Lister les fichiers visibles", description = "Retourne les fichiers visibles pour l utilisateur courant.")
    @ApiResponse(responseCode = "200", description = "Liste des fichiers retournee")
    public PagedResponse<StoredFileResponse> findAll(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "updatedAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        return storageService.findPageForUser(authentication.getName(), pageable);
    }

    @PostMapping("/uploads/presign")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generer une URL d upload presignee", description = "Cree une entree de fichier en attente puis genere une URL PUT presignee vers MinIO.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "URL presignee generee"),
            @ApiResponse(responseCode = "400", description = "Parametres invalides"),
            @ApiResponse(responseCode = "503", description = "Stockage objet indisponible")
    })
    public InitiateFileUploadResponse initiateUpload(
            @Valid @RequestBody InitiateFileUploadRequest request,
            Authentication authentication
    ) {
        return storageService.initiateUpload(request, authentication.getName());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Finaliser un upload direct", description = "Verifie l objet dans MinIO puis marque le fichier comme pret dans la base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier finalise"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Fichier introuvable"),
            @ApiResponse(responseCode = "503", description = "Stockage objet indisponible")
    })
    public StoredFileResponse completeUpload(
            @Parameter(description = "Identifiant du fichier") @PathVariable Long id,
            Authentication authentication
    ) {
        return storageService.completeUpload(id, authentication.getName());
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Generer une URL de telechargement presignee", description = "Retourne une URL GET presignee vers MinIO pour un fichier pret.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL de telechargement generee"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Fichier introuvable"),
            @ApiResponse(responseCode = "503", description = "Stockage objet indisponible")
    })
    public PresignedDownloadResponse createDownloadUrl(
            @Parameter(description = "Identifiant du fichier") @PathVariable Long id,
            Authentication authentication
    ) {
        return storageService.createDownloadUrl(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un fichier", description = "Supprime la metadata locale et l objet distant s il existe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Fichier supprime"),
            @ApiResponse(responseCode = "403", description = "Acces refuse"),
            @ApiResponse(responseCode = "404", description = "Fichier introuvable"),
            @ApiResponse(responseCode = "503", description = "Stockage objet indisponible")
    })
    public void delete(
            @Parameter(description = "Identifiant du fichier") @PathVariable Long id,
            Authentication authentication
    ) {
        storageService.delete(id, authentication.getName());
    }
}
