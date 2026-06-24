package com.example.backend.common.controller;

import com.example.backend.common.dto.DevMailResponse;
import com.example.backend.common.mail.DevMailInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev-mails")
@ConditionalOnProperty(prefix = "app.mail", name = "dev-inbox-enabled", havingValue = "true")
@Tag(name = "Dev Mail", description = "Inbox de developpement pour consulter les emails generes localement")
@SecurityRequirement(name = "bearerAuth")
public class DevMailController {

    private final DevMailInboxService devMailInboxService;

    public DevMailController(DevMailInboxService devMailInboxService) {
        this.devMailInboxService = devMailInboxService;
    }

    @GetMapping
    @Operation(summary = "Lister les emails de dev", description = "Retourne les emails recents captures par la dev inbox.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste retournee"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs")
    })
    public List<DevMailResponse> findAll() {
        return devMailInboxService.findAll().stream()
                .map(entry -> new DevMailResponse(
                        entry.id(),
                        entry.to(),
                        entry.subject(),
                        entry.body(),
                        entry.createdAt()
                ))
                .toList();
    }

    @DeleteMapping
    @Operation(summary = "Vider la dev inbox", description = "Supprime les emails captures par la dev inbox.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inbox videe"),
            @ApiResponse(responseCode = "403", description = "Reserve aux administrateurs")
    })
    public ResponseEntity<Void> clear() {
        devMailInboxService.clear();
        return ResponseEntity.noContent().build();
    }
}
