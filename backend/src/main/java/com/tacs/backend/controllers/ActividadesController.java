package com.tacs.backend.controllers;

import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.services.ActividadesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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
}
