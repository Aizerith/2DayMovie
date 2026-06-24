package com.example.backend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "Verification rapide de disponibilite du backend")
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/api/health")
    @Operation(summary = "Verifier l etat du backend", description = "Retourne l etat de l application et de sa connexion base de donnees.")
    public ResponseEntity<Map<String, Object>> health() {
        boolean databaseUp = databaseAvailable();
        HttpStatus status = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status).body(Map.of(
                "status", databaseUp ? "UP" : "DOWN",
                "components", Map.of(
                        "application", "UP",
                        "database", databaseUp ? "UP" : "DOWN"
                )
        ));
    }

    private boolean databaseAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception exception) {
            return false;
        }
    }
}
