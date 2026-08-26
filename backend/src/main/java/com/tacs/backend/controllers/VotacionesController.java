package com.tacs.backend.controllers;

import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/votaciones")
public class VotacionesController {
    private final VotacionesService votacionesService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<VotacionDto>> getVotaciones(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "true") boolean abierta) {
        Long usuarioId = authService.buscarPorUsername(jwt.getSubject()).id();
        return ResponseEntity.ok(votacionesService.votaciones(usuarioId, abierta));
    }
}
