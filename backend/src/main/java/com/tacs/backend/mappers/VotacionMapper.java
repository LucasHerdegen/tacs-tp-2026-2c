package com.tacs.backend.mappers;

import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.votacion.AlternativaDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class VotacionMapper {
    private final ActividadesMapper actividadesMapper;

    public VotacionMapper(ActividadesMapper actividadesMapper) {
        this.actividadesMapper = actividadesMapper;
    }

    public VotacionDto votacionToVotacionDto(Votacion votacion) {
        return new VotacionDto(
                votacion.getId(),
                votacion.getFechaApertura(),
                actividadesMapper.actividadToActividadDto(votacion.getActividad()),
                votacion.getAlternativas().stream()
                        .map(this::alternativaToAlternativaDto)
                        .collect(Collectors.toList()),
                votacion.getQuorumMinimo(),
                votacion.isAbierta()
        );
    }

    public AlternativaDto alternativaToAlternativaDto(Alternativa alternativa) {
        return new AlternativaDto(
                alternativa.getId(),
                alternativa.getFecha(),
                climaToClimaDto(alternativa.getClima()),
                alternativa.getNumeroAltenativa()
        );
    }

    public ClimaDto climaToClimaDto(Clima clima) {
        if (clima == null) return null;
        return new ClimaDto(clima.getProbabilidadLluvia(), clima.getTemperatura(), clima.getViento());
    }
}
