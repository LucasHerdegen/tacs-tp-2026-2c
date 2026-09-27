package com.tacs.backend.controllers;

import com.tacs.backend.dtos.notificaciones.ContadorNoLeidasDto;
import com.tacs.backend.dtos.notificaciones.NotificacionDto;
import com.tacs.backend.services.NotificacionInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notificaciones")
class NotificacionesController
{
  private final NotificacionInboxService notificacionInboxService;

  @Operation(summary = "Listar mis notificaciones", description = "Devuelve, paginadas, las notificaciones del usuario autenticado (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Pagina de notificaciones", content = @Content(schema = @Schema(implementation = NotificacionDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @GetMapping("/me")
  public ResponseEntity<Page<NotificacionDto>> getMisNotificaciones(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Boolean leida,
      @ParameterObject Pageable pageable)
  {
    String usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(notificacionInboxService.obtenerNotificaciones(usuarioId, leida, pageable));
  }

  @Operation(summary = "Contar notificaciones no leidas", description = "Devuelve la cantidad de notificaciones no leidas del usuario autenticado (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Cantidad de no leidas", content = @Content(schema = @Schema(implementation = ContadorNoLeidasDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @GetMapping("/me/no-leidas/count")
  public ResponseEntity<ContadorNoLeidasDto> getCantidadNoLeidas(@AuthenticationPrincipal Jwt jwt)
  {
    String usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(new ContadorNoLeidasDto(notificacionInboxService.contarNoLeidas(usuarioId)));
  }

  @Operation(summary = "Marcar notificacion como leida", description = "Marca una notificacion puntual del usuario autenticado como leida (Requiere rol USER)")
  @ApiResponse(responseCode = "204", description = "Marcada exitosamente")
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Notificacion no encontrada", content = @Content)
  @PatchMapping("/{id}/leida")
  public ResponseEntity<Void> marcarComoLeida(@PathVariable String id, @AuthenticationPrincipal Jwt jwt)
  {
    String usuarioId = jwt.getClaim("id");
    notificacionInboxService.marcarComoLeida(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Marcar todas como leidas", description = "Marca todas las notificaciones no leidas del usuario autenticado como leidas (Requiere rol USER)")
  @ApiResponse(responseCode = "204", description = "Marcadas exitosamente")
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @PatchMapping("/me/leidas")
  public ResponseEntity<Void> marcarTodasComoLeidas(@AuthenticationPrincipal Jwt jwt)
  {
    String usuarioId = jwt.getClaim("id");
    notificacionInboxService.marcarTodasComoLeidas(usuarioId);
    return ResponseEntity.noContent().build();
  }
}
