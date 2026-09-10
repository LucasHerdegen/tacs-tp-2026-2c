package com.tacs.backend.controllers;

import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.dtos.votacion.VotoPostDto;
import com.tacs.backend.services.VotacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/votaciones")
public class VotacionesController {
    private final VotacionesService votacionesService;

    @Operation(summary = "Obtener votaciones", description = "Devuelve una lista de votaciones del usuario (Requiere rol USER)")
    @ApiResponse(responseCode = "200", description = "Lista de votaciones", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @GetMapping
    public ResponseEntity<List<VotacionDto>> getVotaciones(
            @RequestParam(defaultValue = "true") boolean abierta,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = jwt.getClaim("id");
        return ResponseEntity.ok(votacionesService.votaciones(usuarioId, abierta));
    }

    @Operation(summary = "Crear votación", description = "Crea una votación para una actividad específica (Requiere rol USER)")
    @ApiResponse(responseCode = "201", description = "Votación creada exitosamente", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
    @PostMapping
    public ResponseEntity<VotacionDto> crearVotacion(
            @RequestParam Long actividadId,
            @RequestBody @Valid VotacionPostDto votacionPostDto) {
        VotacionDto votacion = votacionesService.crearVotacion(actividadId, votacionPostDto);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(votacion.id())
                .toUri();

        return ResponseEntity.created(location).body(votacion);
    }

    @Operation(summary = "Obtener votación", description = "Obtiene los detalles de una votación por su ID (Requiere rol USER)")
    @ApiResponse(responseCode = "200", description = "Detalles de la votación", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación no encontrada", content = @Content)
    @GetMapping("/{id}")
    public ResponseEntity<VotacionDto> getVotacion(@PathVariable Long id) {
        return ResponseEntity.ok(votacionesService.obtenerVotacion(id));
    }

    @Operation(summary = "Agregar alternativa", description = "Agrega una alternativa a una votación existente (Requiere rol USER)")
    @ApiResponse(responseCode = "200", description = "Alternativa agregada exitosamente", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación no encontrada", content = @Content)
    @PostMapping("/{id}/alternativas")
    public ResponseEntity<VotacionDto> agregarAlternativa(
            @PathVariable Long id,
            @RequestBody @Valid AlternativaPostDto alternativaPostDto) {
        return ResponseEntity.ok(votacionesService.agregarAlternativa(id, alternativaPostDto));
    }

    @Operation(summary = "Eliminar alternativa", description = "Elimina una alternativa de una votación (Requiere rol USER)")
    @ApiResponse(responseCode = "204", description = "Alternativa eliminada exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación o alternativa no encontrada", content = @Content)
    @DeleteMapping("/{id}/alternativas/{numeroAlternativa}")
    public ResponseEntity<Void> eliminarAlternativa(@PathVariable Long id, @PathVariable int numeroAlternativa) {
        votacionesService.eliminarAlternativa(id, numeroAlternativa);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Votar", description = "Registra un voto en una votación (Requiere rol USER)")
    @ApiResponse(responseCode = "200", description = "Voto registrado exitosamente", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación no encontrada", content = @Content)
    @PostMapping("/{id}/votos")
    public ResponseEntity<VotacionDto> votar(@PathVariable Long id, @RequestBody @Valid VotoPostDto votoPostDto) {
        return ResponseEntity.ok(votacionesService.votar(id, votoPostDto.usuarioId(), votoPostDto.numeroAlternativa()));
    }

    @Operation(summary = "Cerrar votación", description = "Cierra o resuelve una votación abierta (Requiere rol USER)")
    @ApiResponse(responseCode = "200", description = "Votación cerrada exitosamente", content = @Content(schema = @Schema(implementation = VotacionDto.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación no encontrada", content = @Content)
    @PostMapping("/{id}/cierre")
    public ResponseEntity<VotacionDto> cerrarVotacion(@PathVariable Long id) {
        return ResponseEntity.ok(votacionesService.resolverVotacion(id));
    }

    @Operation(summary = "Eliminar votación", description = "Elimina una votación por su ID (Requiere rol USER)")
    @ApiResponse(responseCode = "204", description = "Votación eliminada exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "No autorizado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Votación no encontrada", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVotacion(@PathVariable Long id) {
        votacionesService.eliminarVotacion(id);
        return ResponseEntity.noContent().build();
    }
}