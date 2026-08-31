package com.tacs.backend.dtos.actividades;

import com.tacs.backend.domain.actividad.*;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.dtos.usuario.UsuarioDto;

import java.time.LocalDateTime;
import java.util.List;

public record ActividadDto(Long id,
                           String titulo,
                           String descripcion,
                           TipoActividad tipoActividad,
                           Ubicacion ubicacion,
                           LocalDateTime fecha,
                           int duracionEstimada,
                           int minimoParticipantes,
                           int maximoParticipantes,
                           UsuarioDto organizador,
                           List<UsuarioDto> participantes,
                           int horasAnticipacion,
                           RangoReprogramacion rangoReprogramacion,
                           List<CambioFecha> cambiosFecha,
                           TipoEstadoActividad estadoActividad,
                           ReglasClima reglasClima) {
}
