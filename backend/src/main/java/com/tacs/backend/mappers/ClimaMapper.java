package com.tacs.backend.mappers;

import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.dtos.clima.ClimaDto;
import org.springframework.stereotype.Component;

@Component
public class ClimaMapper {

    public ClimaDto climaToClimaDto(Clima clima) {
        if (clima == null) {
            return null;
        }
        return new ClimaDto(
                clima.getProbabilidadLluvia(),
                clima.getTemperatura(),
                clima.getViento()
        );
    }

    public Clima climaDtoToClima(ClimaDto dto) {
        if (dto == null) {
            return null;
        }
        return new Clima(
                dto.probabilidadLluvia(),
                dto.temperatura(),
                dto.viento()
        );
    }
}
