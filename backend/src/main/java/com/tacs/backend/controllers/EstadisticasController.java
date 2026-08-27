package com.tacs.backend.controllers;

import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.services.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
class EstadisticasController {
    private final EstadisticasService estadisticasService;

    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasDto> getEstadisticas() {
        return ResponseEntity.ok(estadisticasService.obtenerEstadisticas());
    }
}
