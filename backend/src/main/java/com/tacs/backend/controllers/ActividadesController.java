package com.tacs.backend.controllers;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.CambiarEstadoDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.services.ActividadesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
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

  @Operation(summary = "Crear una actividad", description = "Crea una nueva actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "201", description = "Actividad creada exitosamente", content = @Content(schema = @Schema(implementation = ActividadDto.class)))
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
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

  @Operation(summary = "Obtener actividad por ID", description = "Devuelve los detalles de una actividad específica (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Actividad encontrada", content = @Content(schema = @Schema(implementation = ActividadDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
  @GetMapping("/{id}")
  public ResponseEntity<ActividadDto> obtenerActividad(@PathVariable Long id)
  {
    return ResponseEntity.ok(actividadesService.obtenerActividad(id));
  }

  @Operation(summary = "Obtener mis actividades", description = "Devuelve las actividades del usuario autenticado (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Lista de actividades", content = @Content(schema = @Schema(implementation = ActividadDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @GetMapping("/me")
  public ResponseEntity<List<ActividadDto>> getMisActividades(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Boolean organizador,
      @RequestParam(required = false) TipoEstadoActividad estado)
  {
    Long usuarioId = jwt.getClaim("id");

    List<ActividadDto> actividades;
    if (organizador == null)
      actividades = actividadesService.actividadesDelUsuario(usuarioId, estado);
    else if (organizador)
      actividades = actividadesService.actividadesOrganizadas(usuarioId, estado);
    else
      actividades = actividadesService.actividadesParticipadas(usuarioId, estado);


    return ResponseEntity.ok(actividades);
  }

  @Operation(summary = "Buscar actividades", description = "Busca actividades por tipo, barrio o fecha (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Pagina de actividades encontradas")
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @GetMapping
  public ResponseEntity<Page<ActividadDto>> buscarActividades(
      @RequestParam(required = false) TipoActividad tipo,
      @RequestParam(required = false) String barrio,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
      @org.springdoc.core.annotations.ParameterObject org.springframework.data.domain.Pageable pageable)
  {
    return ResponseEntity.ok(actividadesService.buscarActividades(tipo, barrio, fecha, pageable));
  }

  @Operation(summary = "Unirse a actividad", description = "Agrega un participante a una actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "204", description = "Unido exitosamente")
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
  @PostMapping("/{id}/participantes")
  public ResponseEntity<Void> unirseActividad(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    actividadesService.unirseActividad(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Bajarse de actividad", description = "Elimina un participante de una actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "204", description = "Bajado exitosamente")
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
  @DeleteMapping("/{id}/participantes")
  public ResponseEntity<Void> bajarseActividad(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    actividadesService.bajarseActividad(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Obtener clima de actividad", description = "Devuelve el pronóstico del clima para una actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Pronóstico del clima", content = @Content(schema = @Schema(implementation = PronosticoRespuestaDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
  @GetMapping("/{id}/clima")
  public ResponseEntity<PronosticoRespuestaDto> obtenerClimaActividad(@PathVariable Long id,
                                                                      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(actividadesService.obtenerClimaActividad(id, usuarioId));
  }


  @Operation(summary = "Cambiar estado de actividad", description = "Cambia el estado de una actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Estado cambiado exitosamente")
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "403", description = "No autorizado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
  @PatchMapping("/{id}/estado")
  public ResponseEntity<Void> cambiarEstadoActividad(
      @PathVariable Long id,
      @Valid @RequestBody CambiarEstadoDto dto,
      @AuthenticationPrincipal Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    actividadesService.cambiarEstado(id, usuarioId, dto.estado());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Actualizar configuración de clima", description = "Actualiza las condiciones climáticas de una actividad (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Actividad actualizada", content = @Content(schema = @Schema(implementation = ActividadDto.class)))
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "403", description = "No autorizado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Actividad no encontrada", content = @Content)
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
