package com.tacs.backend.controllers;

import com.tacs.backend.dtos.usuario.ActualizarRolRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/usuarios")
class UsuariosController
{
  private final AuthService authService;

  @PatchMapping("/{usuarioId}/rol")
  public ResponseEntity<UsuarioDto> actualizarRol(
      @PathVariable Long usuarioId,
      @RequestBody @Valid ActualizarRolRequest request)
  {
    return ResponseEntity.ok(authService.actualizarRol(usuarioId, request.rol()));
  }
}
