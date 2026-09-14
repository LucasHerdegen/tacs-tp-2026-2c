package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "actividades")
@Getter
@Setter
@NoArgsConstructor
public class ActividadEntity
{
  @Id
  private String id;

  @Version
  private Long version;

  private String titulo;

  private String descripcion;

  private TipoActividad tipo;

  private UbicacionEntity ubicacion;

  private LocalDateTime fechaCreacion;

  private LocalDateTime fechaRealizacion;

  private int duracionEstimada;

  private int minimoParticipantes;
  private int maximoParticipantes;

  private boolean recordatorioEnviado = false;

  @DocumentReference
  private UsuarioEntity organizador;

  @DocumentReference
  private List<UsuarioEntity> participantes = new ArrayList<>();

  private int horasAnticipacion;

  private RangoReprogramacionEntity rangoReprogramacion;

  private List<CambioFechaEntity> cambiosFecha = new ArrayList<>();

  private TipoEstadoActividad estado;

  private ReglasClimaEntity reglasClima;
}
