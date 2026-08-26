package com.tacs.backend.dtos.clima;

import com.tacs.backend.domain.clima.Clima;

public record PronosticoRespuestaDto(Clima climaActual, Clima pronosticoFuturo) {
}
