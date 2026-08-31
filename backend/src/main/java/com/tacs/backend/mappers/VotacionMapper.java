package com.tacs.backend.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
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
        Alternativa ganadora = votacion.getAlternativaGanadora();

        return new VotacionDto(
                votacion.getId(),
                votacion.getFechaApertura(),
                votacion.getFechaLimite(),
                votacion.getFechaCierre(),
                actividadesMapper.actividadToActividadDto(votacion.getActividad()),
                votacion.getAlternativas().stream()
                        .map(alternativa -> alternativaToAlternativaDto(alternativa, votacion))
                        .collect(Collectors.toList()),
                votacion.getQuorumMinimo(),
                votacion.isAbierta(),
                ganadora == null ? null : alternativaToAlternativaDto(ganadora, votacion)
        );
    }

    // Ahora recibe la Votacion para poder contar los votos de esta alternativa puntual
    public AlternativaDto alternativaToAlternativaDto(Alternativa alternativa, Votacion votacion) 
    {
        return new AlternativaDto(
                alternativa.getId(),
                alternativa.getFecha(),
                climaToClimaDto(alternativa.getClima()),
                alternativa.getNumeroAltenativa(),
                votacion.cantidadVotos(alternativa),
                cumpleReglasClima(votacion.getActividad(), alternativa)
        );
    }

    public ClimaDto climaToClimaDto(Clima clima) {
        if (clima == null) return null;
        return new ClimaDto(clima.getProbabilidadLluvia(), clima.getTemperatura(), clima.getViento());
    }

    /**
     * null si la actividad no tiene ReglasClima definidas (no aplica); true/false
     * segun si el pronostico de la alternativa las cumple, en caso contrario.
     * No bloquea la creacion de alternativas manuales con mal pronostico, solo
     * informa: ver discusion en la sesion sobre por que no se valida esto.
     */
    private Boolean cumpleReglasClima(Actividad actividad, Alternativa alternativa) {
        ReglasClima reglas = actividad.getReglasClima();
        if (reglas == null || alternativa.getClima() == null) return null;
        return reglas.esFavorable(alternativa.getClima());
    }
}