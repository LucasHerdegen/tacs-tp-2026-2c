package com.tacs.backend.controllers;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.AuthService;
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
@RequestMapping("/api/actividades")
class ActividadesController
{
  private final ActividadesService actividadesService;
  private final AuthService authService;
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

  @GetMapping("/me")
  public ResponseEntity<List<ActividadDto>> getMisActividades(
          @AuthenticationPrincipal Jwt jwt,
          @RequestParam(required = false) Boolean organizador,
          @RequestParam(required = false) TipoEstadoActividad estado) {
    Long usuarioId = authService.buscarPorUsername(jwt.getSubject()).id();

    List<ActividadDto> actividades;
    if(organizador==null) {
      actividades = actividadesService.actividadesDelUsuario(usuarioId, estado);
    } else if (organizador) {
      actividades = actividadesService.actividadesOrganizadas(usuarioId, estado);
    } else {
      actividades = actividadesService.actividadesParticipadas(usuarioId, estado);
    }

    return ResponseEntity.ok(actividades);
  }
}
