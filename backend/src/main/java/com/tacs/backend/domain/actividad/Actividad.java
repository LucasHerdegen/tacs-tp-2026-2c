package com.tacs.backend.domain.actividad;

import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.dtos.clima.ReglasClimaDto;

import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.EstadoInvalidoException;
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

  /**
   * Marca que el recordatorio de inicio ya fue enviado para esta actividad.
   */
  public void marcarRecordatorioEnviado() {
    this.recordatorioEnviado = true;
  }

  /**
   * Evalua si un pronostico climatico dado cumple con las reglas configuradas
   * para que la actividad pueda realizarse normalmente.
   *
   * @param clima El pronostico a evaluar.
   * @return true si el clima es favorable o no hay reglas; false en caso contrario.
   */
  public boolean cumpleCondiciones(Clima clima)
  {
    if (this.reglasClima == null)
      return true;
    return this.reglasClima.esFavorable(clima);
  }

  /**
   * Agrega un nuevo participante a la actividad, validando la capacidad maxima
   * y que el usuario no este previamente inscripto.
   *
   * @param usuario El usuario a inscribir.
   * @throws CapacidadMaximaException Si la actividad ya alcanzo el maximo de participantes.
   */
  public void agregarParticipante(Usuario usuario)
  {
    if (this.participantes.size() >= this.maximoParticipantes)
      throw new CapacidadMaximaException("La actividad ha alcanzado la capacidad maxima de participantes");

    if (!this.participantes.contains(usuario))
      this.participantes.add(usuario);
  }

  /**
   * Da de baja a un participante de la actividad. El organizador no puede ser removido.
   *
   * @param usuario El usuario que desea bajarse.
   * @throws AccesoDenegadoException Si el usuario a remover es el organizador.
   */
  public void removerParticipante(Usuario usuario)
  {
    if (this.organizador != null && this.organizador.equals(usuario))
      throw new AccesoDenegadoException("El organizador no puede bajarse de la actividad");

    this.participantes.remove(usuario);
  }

  /**
   * Reprograma la actividad a una nueva fecha, guardando un registro del cambio
   * y actualizando el estado a REPROGRAMADA.
   *
   * @param date La nueva fecha de realizacion.
   */
  public void reprogramar(LocalDateTime date)
  {
    CambioFecha cambio = new CambioFecha(LocalDateTime.now(), this.fechaRealizacion, date);
    this.cambiosFecha.add(cambio);
    this.fechaRealizacion = date;
    
    if (this.estado != null)
      this.cambiarEstado(TipoEstadoActividad.REPROGRAMADA);
      
    this.recordatorioEnviado = false;
  }

  /**
   * Cambia el estado actual de la actividad validando las transiciones permitidas.
   *
   * @param nuevoEstado El estado al cual se desea pasar.
   * @throws EstadoInvalidoException Si la transicion desde el estado actual no es valida.
   */
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
