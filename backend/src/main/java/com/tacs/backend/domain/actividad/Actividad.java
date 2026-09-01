package com.tacs.backend.domain.actividad;

import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.dtos.clima.ReglasClimaDto;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Actividad
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String titulo;
  private String descripcion;

  @Enumerated(EnumType.STRING)
  private TipoActividad tipo;

  @Embedded
  private Ubicacion ubicacion;

  private LocalDateTime fechaCreacion;
  private LocalDateTime fechaRealizacion;
  private int duracionEstimada;
  private int minimoParticipantes;
  private int maximoParticipantes;
  private boolean recordatorioEnviado;


  @ManyToOne
  private Usuario organizador;

  @ManyToMany
  @JoinTable(
      name = "actividad_participantes",
      joinColumns = @JoinColumn(name = "actividad_id"),
      inverseJoinColumns = @JoinColumn(name = "usuario_id")
  )
  private List<Usuario> participantes = new ArrayList<>();

  private int horasAnticipacion;

  @Embedded
  private RangoReprogramacion rangoReprogramacion;

  @ElementCollection
  @CollectionTable(name = "actividad_cambios_fecha", joinColumns = @JoinColumn(name = "actividad_id"))
  private List<CambioFecha> cambiosFecha = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  private TipoEstadoActividad estado;

  @Embedded
  private ReglasClima reglasClima;

  public Actividad(String titulo, String descripcion, TipoActividad tipoActividad, Ubicacion ubicacion,
                   LocalDateTime fechaRealizacion, int duracionEstimada,
                   LocalDateTime fechaCreacion, int minimoParticipantes, int maximoParticipantes, Usuario organizador)
  {
    this.titulo = titulo;
    this.descripcion = descripcion;
    this.tipo = tipoActividad;
    this.ubicacion = ubicacion;
    this.duracionEstimada = duracionEstimada;
    this.fechaCreacion = fechaCreacion;
    this.fechaRealizacion = fechaRealizacion;
    this.minimoParticipantes = minimoParticipantes;
    this.maximoParticipantes = maximoParticipantes;
    this.organizador = organizador;
    this.agregarParticipante(organizador);
  }

  public void marcarRecordatorioEnviado() {
    this.recordatorioEnviado = true;
  }

  public boolean cumpleCondiciones(Clima clima)
  {
    return reglasClima != null && reglasClima.esFavorable(clima);
  }

  public void agregarParticipante(Usuario usuario)
  {
    if (!participantes.contains(usuario))
      participantes.add(usuario);
  }

  public void removerParticipante(Usuario usuario)
  {
    participantes.remove(usuario);
  }

  public void reprogramar(LocalDateTime date)
  {
    CambioFecha cambio = new CambioFecha(LocalDateTime.now(), this.fechaRealizacion, date);
    this.cambiosFecha.add(cambio);
    this.fechaRealizacion = date;
    this.recordatorioEnviado = false;
    
    if (estado != null)
      this.cambiarEstado(TipoEstadoActividad.REPROGRAMADA);
  }

  public void cambiarEstado(TipoEstadoActividad nuevoEstado)
  {
    if (this.estado == null)
      this.estado = nuevoEstado;
    else
      Estados.getEstado(this.estado).cambiarEstado(this, nuevoEstado);
  }

  public void actualizarReglasClima(ReglasClimaDto dto)
  {
    if (this.reglasClima == null)
    this.reglasClima = new ReglasClima();

    this.reglasClima.actualizar(dto);
  }

  public void actualizarHorasAnticipacion(Integer horas)
  {
    if (horas != null) {
      this.horasAnticipacion = horas;
    }
  }

  public void actualizarRangoReprogramacion(RangoReprogramacionDto dto)
  {
    if (this.rangoReprogramacion == null)
    this.rangoReprogramacion = new RangoReprogramacion();

    this.rangoReprogramacion.actualizar(dto);
  }
}
