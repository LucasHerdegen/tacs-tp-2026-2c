package com.tacs.backend.services.implem;


import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
class EstadisticasServiceImplem implements EstadisticasService {
    private final ActividadesRepository actividadesRepository;

    // calculo de estadisticas para el US 14
    // TODO queda fuera de mi alcance "canceladas por clima" y "consultas al servicio de pronostico"
    // porque todavia no estan implementadas
    @Override
    public EstadisticasDto obtenerEstadisticas() {
        // de quererlo aca puedo sacar un par, esto es como un dashboard
        long creadas = actividadesRepository.count();
        long reprogramadas = actividadesRepository.countByEstadoTipo(TipoEstadoActividad.REPROGRAMADA);
        long canceladas = actividadesRepository.countByEstadoTipo(TipoEstadoActividad.CANCELADA);
        long confirmadas = actividadesRepository.countByEstadoTipo(TipoEstadoActividad.CONFIRMADA);
        long finalizadas = actividadesRepository.countByEstadoTipo(TipoEstadoActividad.FINALIZADA);

        return new EstadisticasDto(creadas, reprogramadas, canceladas, confirmadas, finalizadas);
    }
}
