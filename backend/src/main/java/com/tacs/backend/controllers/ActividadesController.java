package com.tacs.backend.controllers;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.services.ActividadesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.apache.coyote.Response;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
import java.time.LocalDate;
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

  @GetMapping
  public ResponseEntity<List<ActividadDto>> buscarActividades(
      @RequestParam(required = false) TipoActividad tipo,
      @RequestParam(required = false) String barrio,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha)
  {
    List<ActividadDto> actividades = actividadesService.buscarActividades(tipo, barrio, fecha);
    return ResponseEntity.ok(actividades);
  }

  @PostMapping("/{id}/participantes")
  public ResponseEntity<Void> unirseActividad(@PathVariable Long id, @RequestParam Long usuarioId)
  {
    actividadesService.unirseActividad(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/participantes")
  public ResponseEntity<Void> bajarseActividad(@PathVariable Long id, @RequestParam Long usuarioId)
  {
    actividadesService.bajarseActividad(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/clima")
  public ResponseEntity<PronosticoRespuestaDto> obtenerClimaActividad(@PathVariable Long id, @RequestParam Long usuarioId) {
    return ResponseEntity.ok(actividadesService.obtenerClimaActividad(id, usuarioId));
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
