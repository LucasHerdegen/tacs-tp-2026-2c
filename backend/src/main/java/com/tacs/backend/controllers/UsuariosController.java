package com.tacs.backend.controllers;

import com.tacs.backend.dtos.usuario.ActualizarRolRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tacs.backend.dtos.usuario.ActualizarContactoDto;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/usuarios")
class UsuariosController
{
  private final AuthService authService;

  @Operation(summary = "Actualizar rol de usuario", description = "Modifica el rol de un usuario existente (Requiere rol ADMIN)")
  @ApiResponse(responseCode = "200", description = "Rol actualizado exitosamente", content = @Content(schema = @Schema(implementation = UsuarioDto.class)))
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "403", description = "No autorizado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content)
  @PatchMapping("/{usuarioId}/rol")
  public ResponseEntity<UsuarioDto> actualizarRol(
      @PathVariable Long usuarioId,
      @RequestBody @Valid ActualizarRolRequest request)
  {
    return ResponseEntity.ok(authService.actualizarRol(usuarioId, request.rol()));
  }

  @Operation(summary = "Obtener datos propios", description = "Devuelve los datos del usuario autenticado (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Usuario encontrado", content = @Content(schema = @Schema(implementation = UsuarioDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @org.springframework.web.bind.annotation.GetMapping("/me")
  public ResponseEntity<UsuarioDto> obtenerMiUsuario(
      @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(authService.obtenerUsuario(usuarioId));
  }

  @Operation(summary = "Actualizar mi contacto", description = "Actualiza las preferencias de contacto del usuario autenticado (Requiere rol USER)")
  @ApiResponse(responseCode = "200", description = "Contacto actualizado", content = @Content(schema = @Schema(implementation = UsuarioDto.class)))
  @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @PatchMapping("/me/contacto")
  public ResponseEntity<UsuarioDto> actualizarMiContacto(
      @RequestBody @Valid ActualizarContactoDto dto,
      @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt)
  {
    Long usuarioId = jwt.getClaim("id");
    return ResponseEntity.ok(authService.actualizarContacto(usuarioId, dto.medioContacto()));
  }
}
