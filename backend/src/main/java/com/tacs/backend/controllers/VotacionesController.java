package com.tacs.backend.controllers;

import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/votaciones")
public class VotacionesController {
    private final VotacionesService votacionesService;

    @GetMapping
    public ResponseEntity<List<VotacionDto>> getVotaciones(
            @RequestParam Long usuarioId,
            @RequestParam(defaultValue = "true") boolean abierta) {
        return ResponseEntity.ok(votacionesService.votaciones(usuarioId, abierta));
    }
}
