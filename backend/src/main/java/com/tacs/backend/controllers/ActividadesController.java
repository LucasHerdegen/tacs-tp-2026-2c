package com.tacs.backend.controllers;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.services.ActividadesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/actividades")
class ActividadesController
{
  private final ActividadesService actividadesService;

  @PostMapping
  public ResponseEntity<ActividadDto> createActividad(
      @RequestBody @Valid ActividadPostDto actividadPostDto,
      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    var actividad = this.actividadesService.createActividad(actividadPostDto, usuarioId);

    URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(actividad.id())
        .toUri();

    return ResponseEntity.created(location).body(actividad);
  }

  @GetMapping("/organizadas")
  public ResponseEntity<List<ActividadDto>> getActividadesOrganizadas(
      @RequestParam(required = false) TipoEstadoActividad estado,
      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(actividadesService.actividadesOrganizadas(usuarioId, estado));
  }

  @GetMapping("/participadas")
  public ResponseEntity<List<ActividadDto>> getActividadesParticipadas(
      @RequestParam(required = false) TipoEstadoActividad estado,
      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(actividadesService.actividadesParticipadas(usuarioId, estado));
  }

  @PostMapping("/{id}/cancelaciones")
  public ResponseEntity<Void> cancelarActividad(
      @PathVariable Long id,
      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    actividadesService.cancelarActividad(id, usuarioId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PatchMapping("/{id}/configuracion-clima")
  public ResponseEntity<ActividadDto> actualizarConfiguracionClima(
      @PathVariable Long id,
      @Valid @RequestBody ConfigurarCondicionesDto dto,
      @AuthenticationPrincipal Jwt jwt) 
  {
    Long usuarioId = jwt.getClaim("id");
    ActividadDto actividadActualizada = actividadesService.actualizarConfiguracionClima(id, usuarioId, dto);
    return ResponseEntity.ok(actividadActualizada);
  }
}
