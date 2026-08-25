package com.tacs.backend.controllers;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.services.ActividadesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

  @GetMapping
  public ResponseEntity<List<ActividadDto>> buscarActividades(
      @RequestParam(required = false) TipoActividad tipo,
      @RequestParam(required = false) String barrio,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha)
  {
    List<ActividadDto> actividades = actividadesService.buscarActividades(tipo, barrio, fecha);
    return ResponseEntity.ok(actividades);
  }

  @PostMapping("/api/{id}/participantes")
  public ResponseEntity<Void> unirseActividad(@PathVariable Long id, @RequestParam Long usuarioId)
  {
    actividadesService.unirseActividad(id, usuarioId);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/api/{id}/participantes")
  public ResponseEntity<Void> bajarseActividad(@PathVariable Long id, @RequestParam Long usuarioId)
  {
    actividadesService.bajarseActividad(id, usuarioId);
    return ResponseEntity.ok().build();
  }
}
