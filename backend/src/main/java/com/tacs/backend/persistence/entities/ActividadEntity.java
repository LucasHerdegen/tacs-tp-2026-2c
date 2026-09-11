package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "actividades")
@Getter
@Setter
@NoArgsConstructor
public class ActividadEntity
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(nullable = false)
  private String titulo;

  @Column(nullable = false, length = 1000)
  private String descripcion;

  @Enumerated(EnumType.STRING)
  private TipoActividad tipo;

  @Embedded
  private UbicacionEntity ubicacion;

  @Column(nullable = false, updatable = false)
  private LocalDateTime fechaCreacion;

  @Column(nullable = false)
  private LocalDateTime fechaRealizacion;

  @Column(nullable = false)
  private int duracionEstimada;

  private int minimoParticipantes;
  private int maximoParticipantes;

  @Column(nullable = false)
  private boolean recordatorioEnviado = false;

  @ManyToOne(optional = false, cascade = CascadeType.ALL)
  @JoinColumn(name = "organizador_id", nullable = false)
  private UsuarioEntity organizador;

  @ManyToMany(cascade = CascadeType.ALL)
  @JoinTable(
      name = "actividad_participantes",
      joinColumns = @JoinColumn(name = "actividad_id"),
      inverseJoinColumns = @JoinColumn(name = "usuario_id"),
      uniqueConstraints = @UniqueConstraint(columnNames = {"actividad_id", "usuario_id"})
  )
  private List<UsuarioEntity> participantes = new ArrayList<>();

  private int horasAnticipacion;
  @Embedded
  private RangoReprogramacionEntity rangoReprogramacion;

  @ElementCollection
  private List<CambioFechaEntity> cambiosFecha = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  private TipoEstadoActividad estado;

  @Embedded
  private ReglasClimaEntity reglasClima;
}
