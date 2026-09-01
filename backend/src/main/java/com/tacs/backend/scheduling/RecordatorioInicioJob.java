package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.ServicioNotificaciones;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class RecordatorioInicioJob {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActividadesRepository actividadesRepository;
    private final ServicioNotificaciones servicioNotificaciones;
    private final int horasAnticipacionDefault;

    public RecordatorioInicioJob(ActividadesRepository actividadesRepository,
                                 ServicioNotificaciones servicioNotificaciones,
                                 @Value("${recordatorio.inicio.horas-anticipacion-default}") int horasAnticipacionDefault) {
        this.actividadesRepository = actividadesRepository;
        this.servicioNotificaciones = servicioNotificaciones;
        this.horasAnticipacionDefault = horasAnticipacionDefault;
    }

    @Scheduled(fixedRateString = "${recordatorio.inicio.intervalo-ms}")
    public void enviarRecordatorios() {
        for (Actividad actividad : detectarActividadesPorComenzar())
            notificarSinRomperLoop(actividad);
    }

    List<Actividad> detectarActividadesPorComenzar() {
        return actividadesRepository.findCandidatasParaRecordatorio().stream()
                .filter(this::dentroDeVentanaAnticipacion)
                .toList();
    }

    private boolean dentroDeVentanaAnticipacion(Actividad actividad) {
        int anticipacion = actividad.getHorasAnticipacion() > 0
                ? actividad.getHorasAnticipacion()
                : horasAnticipacionDefault;
        return !LocalDateTime.now().plusHours(anticipacion).isBefore(actividad.getFechaRealizacion());
    }

    private void notificarSinRomperLoop(Actividad actividad) {
        try {
            servicioNotificaciones.notificarATodos(
                    "La actividad '%s' comienza el %s.".formatted(
                            actividad.getTitulo(), actividad.getFechaRealizacion().format(FORMATO)),
                            actividad.getParticipantes());
            actividad.marcarRecordatorioEnviado();
            actividadesRepository.save(actividad);
        } catch (Exception e) {
            log.error("Fallo enviando el recordatorio de la actividad id={}, se reintenta en la proxima corrida",
                    actividad.getId(), e);
        }
    }
}
