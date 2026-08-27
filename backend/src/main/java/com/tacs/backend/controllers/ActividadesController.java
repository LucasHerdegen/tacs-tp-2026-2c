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

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/actividades")
class ActividadesController
{
  private final ActividadesService actividadesService;

  @PostMapping
  public ResponseEntity<ActividadDto> createActividad(@RequestBody @Valid ActividadPostDto actividadPostDto)
  {
    var actividad = this.actividadesService.createActividad(actividadPostDto);

    URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(actividad.id())
        .toUri();

    return ResponseEntity.created(location).body(actividad);
  }

  @GetMapping("/organizador/{usuarioId}")   // TODO definir estructura endpoint? -> path param?
  public ResponseEntity<List<ActividadDto>> getActividadesOrganizadas(
      @PathVariable Long usuarioId,
      @RequestParam(required = false) TipoEstadoActividad estado)
  {
    return ResponseEntity.ok(actividadesService.actividadesOrganizadas(usuarioId, estado));
  }

  @GetMapping("/participante/{usuarioId}")
  public ResponseEntity<List<ActividadDto>> getActividadesParticipadas(
      @PathVariable Long usuarioId,
      @RequestParam(required = false) TipoEstadoActividad estado)
  {
    return ResponseEntity.ok(actividadesService.actividadesParticipadas(usuarioId, estado));
  }

  @PostMapping("/{id}/cancelaciones")
  public ResponseEntity<Void> cancelarActividad(@PathVariable Long id)
  {
    // TODO: recuperar usuarioId del JWT
    Long usuarioMocakeadoId = 1L;
    actividadesService.cancelarActividad(id, usuarioMocakeadoId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PatchMapping("/{id}/configuracion-clima")
  public ResponseEntity<ActividadDto> actualizarConfiguracionClima(
      @PathVariable Long id,
      @RequestBody ConfigurarCondicionesDto dto) 
  {
    // TODO: Recuperar usuarioId del JWT
    Long usuarioIdMock = 1L; 
    
    ActividadDto actividadActualizada = actividadesService.actualizarConfiguracionClima(id, usuarioIdMock, dto);
    return ResponseEntity.ok(actividadActualizada);
  }
}
