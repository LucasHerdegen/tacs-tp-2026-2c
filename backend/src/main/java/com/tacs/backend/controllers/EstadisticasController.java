package com.tacs.backend.controllers;

import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.services.EstadisticasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
class EstadisticasController
{
  private final EstadisticasService estadisticasService;

  @Operation(summary = "Obtener estadísticas", description = "Devuelve estadísticas globales del sistema (Requiere rol ADMIN)")
  @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas", content = @Content(schema = @Schema(implementation = EstadisticasDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "403", description = "No autorizado", content = @Content)
  @GetMapping("/estadisticas")
  public ResponseEntity<EstadisticasDto> getEstadisticas()
  {
    return ResponseEntity.ok(estadisticasService.obtenerEstadisticas());
  }
}
