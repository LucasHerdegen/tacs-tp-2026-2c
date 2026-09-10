package com.tacs.backend.controllers;

import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
class AuthController
{
  private final AuthService authService;

  @Operation(summary = "Registrar usuario", description = "Registra un nuevo usuario en el sistema (Público)")
  @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente", content = @Content(schema = @Schema(implementation = UsuarioDto.class)))
  @ApiResponse(responseCode = "400", description = "Datos de registro inválidos", content = @Content)
  @PostMapping("/register")
  public ResponseEntity<UsuarioDto> register(@RequestBody @Valid RegistroRequest request)
  {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
  }

  @Operation(summary = "Iniciar sesión", description = "Inicia sesión con credenciales y devuelve un token (Público)")
  @ApiResponse(responseCode = "200", description = "Inicio de sesión exitoso", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
  @ApiResponse(responseCode = "400", description = "Credenciales inválidas", content = @Content)
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request)
  {
    return ResponseEntity.ok(authService.login(request));
  }

  @Operation(summary = "Obtener datos del usuario actual", description = "Devuelve los datos del usuario autenticado (Requiere rol USER o superior)")
  @ApiResponse(responseCode = "200", description = "Datos del usuario", content = @Content(schema = @Schema(implementation = UsuarioDto.class)))
  @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
  @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content)
  @GetMapping("/me")
  public ResponseEntity<UsuarioDto> me(@AuthenticationPrincipal Jwt jwt)
  {
    return ResponseEntity.ok(authService.buscarPorUsername(jwt.getSubject()));
  }
}
