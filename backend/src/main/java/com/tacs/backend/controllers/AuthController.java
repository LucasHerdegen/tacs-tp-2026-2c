package com.tacs.backend.controllers;

import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @PostMapping("/register")
  public ResponseEntity<UsuarioDto> register(@RequestBody @Valid RegistroRequest request)
  {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request)
  {
    return ResponseEntity.ok(authService.login(request));
  }
}
