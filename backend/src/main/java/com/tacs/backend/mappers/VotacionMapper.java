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
                        .map(alternativa -> alternativaToAlternativaDto(alternativa, votacion))
                        .collect(Collectors.toList()),
                votacion.getQuorumMinimo(),
                votacion.isAbierta()
        );
    }

    // ahora recibe la Votacion para poder contar los votos de esta alternativa puntual
    public AlternativaDto alternativaToAlternativaDto(Alternativa alternativa, Votacion votacion) {
        long cantidadVotos = votacion.getVotos().stream()
                .filter(voto -> voto.getAlternativa().getId().equals(alternativa.getId()))
                .count();

        return new AlternativaDto(
                alternativa.getId(),
                alternativa.getFecha(),
                climaToClimaDto(alternativa.getClima()),
                alternativa.getNumeroAltenativa(),
                cantidadVotos
        );
    }

    public ClimaDto climaToClimaDto(Clima clima) {
        if (clima == null) return null;
        return new ClimaDto(clima.getProbabilidadLluvia(), clima.getTemperatura(), clima.getViento());
    }
}