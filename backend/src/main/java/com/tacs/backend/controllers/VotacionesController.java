package com.tacs.backend.controllers;

import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/votaciones")
public class VotacionesController {
    private final VotacionesService votacionesService;

    @GetMapping("/abiertas/{usuarioId}")
    public ResponseEntity<List<VotacionDto>> getVotacionesAbiertas(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(votacionesService.votacionesAbiertas(usuarioId));
    }
}
