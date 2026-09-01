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
    // TODO queda fuera de mi alcance "consultas al servicio de pronostico"
    // porque todavia no estan implementadas. Ademas, el proveedor de clima (ProveedorClima) hoy es
    // el mock MockProveedorClima: contar sus llamadas no reflejaria consultas reales a ningun servicio,
    // asi que no tiene sentido implementarlo hasta que haya un proveedor real.
    @Override
    public EstadisticasDto obtenerEstadisticas() {
        // de quererlo aca puedo sacar un par, esto es como un dashboard
        long creadas = actividadesRepository.count();
        long reprogramadas = actividadesRepository.countByEstado(TipoEstadoActividad.REPROGRAMADA);
        long canceladas = actividadesRepository.countByEstado(TipoEstadoActividad.CANCELADA);
        long confirmadas = actividadesRepository.countByEstado(TipoEstadoActividad.CONFIRMADA);
        long finalizadas = actividadesRepository.countByEstado(TipoEstadoActividad.FINALIZADA);

        return new EstadisticasDto(creadas, reprogramadas, canceladas, confirmadas, finalizadas);
    }
}
