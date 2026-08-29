package com.tacs.backend.controllers;

import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.dtos.votacion.VotoPostDto;
import com.tacs.backend.services.VotacionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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

    @PostMapping
    public ResponseEntity<VotacionDto> crearVotacion(
            @RequestParam Long actividadId,
            @RequestBody @Valid VotacionPostDto votacionPostDto) {
        VotacionDto votacion = votacionesService.crearVotacion(actividadId, votacionPostDto);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(votacion.id())
                .toUri();

        return ResponseEntity.created(location).body(votacion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VotacionDto> getVotacion(@PathVariable Long id) {
        return ResponseEntity.ok(votacionesService.obtenerVotacion(id));
    }

    @PostMapping("/{id}/alternativas")
    public ResponseEntity<VotacionDto> agregarAlternativa(
            @PathVariable Long id,
            @RequestBody @Valid AlternativaPostDto alternativaPostDto) {
        return ResponseEntity.ok(votacionesService.agregarAlternativa(id, alternativaPostDto));
    }

    @DeleteMapping("/{id}/alternativas/{numeroAlternativa}")
    public ResponseEntity<Void> eliminarAlternativa(@PathVariable Long id, @PathVariable int numeroAlternativa) {
        votacionesService.eliminarAlternativa(id, numeroAlternativa);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/votos")
    public ResponseEntity<VotacionDto> votar(@PathVariable Long id, @RequestBody @Valid VotoPostDto votoPostDto) {
        return ResponseEntity.ok(votacionesService.votar(id, votoPostDto.usuarioId(), votoPostDto.numeroAlternativa()));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<VotacionDto> cerrarVotacion(@PathVariable Long id) {
        return ResponseEntity.ok(votacionesService.resolverVotacion(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVotacion(@PathVariable Long id) {
        votacionesService.eliminarVotacion(id);
        return ResponseEntity.noContent().build();
    }
}