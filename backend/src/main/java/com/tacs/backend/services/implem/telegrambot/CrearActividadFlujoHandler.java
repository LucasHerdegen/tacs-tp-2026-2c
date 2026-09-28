package com.tacs.backend.services.implem.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.UbicacionDto;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Wizard paso a paso de "/crear": junta los datos de un ActividadPostDto en
 * TelegramSesion.datosParciales (JSON) a medida que el usuario responde cada
 * pregunta, y al completar el ultimo paso llama a ActividadesService.createActividad
 * — el mismo service que usa el controller REST, sin logica de negocio propia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrearActividadFlujoHandler implements FlujoHandler
{
  public static final String FLUJO = "CREAR_ACTIVIDAD";
  public static final String PREFIJO_CALLBACK_TIPO = "crear_tipo";

  static final String PASO_TITULO = "TITULO";
  static final String PASO_TIPO = "TIPO";
  static final String PASO_UBICACION = "UBICACION";
  static final String PASO_FECHA = "FECHA";
  static final String PASO_DURACION = "DURACION";
  static final String PASO_CUPO_MINIMO = "CUPO_MINIMO";
  static final String PASO_CUPO_MAXIMO = "CUPO_MAXIMO";

  // Claves que ya tienen que estar guardadas cuando se llega al ultimo paso.
  private static final List<String> CLAVES_PREVIAS_AL_CUPO_MAXIMO =
      List.of("titulo", "tipoActividad", "ciudad", "fecha", "duracionEstimada", "cantidadMinima");

  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm");

  private final TelegramBot telegramBot;
  private final TelegramSesionMongoRepository sesionRepository;
  private final ActividadesService actividadesService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String flujo()
  {
    return FLUJO;
  }

  @Override
  public void manejar(Message mensaje, TelegramSesionEntity sesion, String usuarioId)
  {
    long chatId = sesion.getChatId();
    String texto = mensaje.text() == null ? "" : mensaje.text().trim();

    switch (sesion.getPasoActual())
    {
      case PASO_TITULO -> manejarTitulo(chatId, sesion, texto);
      case PASO_TIPO -> telegramBot.execute(
          new SendMessage(chatId, "Elegi una opcion tocando uno de los botones de arriba."));
      case PASO_UBICACION -> manejarUbicacion(chatId, sesion, texto);
      case PASO_FECHA -> manejarFecha(chatId, sesion, texto);
      case PASO_DURACION -> manejarDuracion(chatId, sesion, texto);
      case PASO_CUPO_MINIMO -> manejarCupoMinimo(chatId, sesion, texto);
      case PASO_CUPO_MAXIMO -> manejarCupoMaximo(chatId, sesion, texto, usuarioId);
      default -> TelegramFlujoUtils.reiniciarSesionCorrupta(log, telegramBot, sesionRepository, chatId,
            "pasoActual desconocido '" + sesion.getPasoActual() + "'", "/crear");
    }
  }

  /**
   * Invocado por CrearTipoCallbackHandler cuando el usuario toca uno de los
   * botones de TipoActividad — el unico paso del wizard que no llega como
   * texto libre sino como CallbackQuery.
   */
  public String manejarSeleccionTipo(TelegramSesionEntity sesion, TipoActividad tipoElegido)
  {
    if (!PASO_TIPO.equals(sesion.getPasoActual()))
      return "Esa opcion ya no es valida.";

    long chatId = sesion.getChatId();
    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("tipoActividad", tipoElegido.name());
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_UBICACION);

    telegramBot.execute(new SendMessage(chatId, "¿En que ciudad es?"));
    return "Tipo: " + tipoElegido.name();
  }

  private void manejarTitulo(long chatId, TelegramSesionEntity sesion, String texto)
  {
    if (texto.isBlank())
    {
      telegramBot.execute(new SendMessage(chatId, "El titulo no puede estar vacio. ¿Cual es el titulo?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("titulo", texto);
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_TIPO);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    for (TipoActividad tipo : TipoActividad.values())
      markup.addRow(new InlineKeyboardButton(tipo.name()).callbackData(PREFIJO_CALLBACK_TIPO + ":" + tipo.name()));

    telegramBot.execute(new SendMessage(chatId, "¿Que tipo de actividad es?").replyMarkup(markup));
  }

  private void manejarUbicacion(long chatId, TelegramSesionEntity sesion, String texto)
  {
    if (texto.isBlank())
    {
      telegramBot.execute(new SendMessage(chatId, "La ciudad no puede estar vacia. ¿En que ciudad es?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("ciudad", texto);
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_FECHA);

    telegramBot.execute(new SendMessage(chatId,
        "¿Cuando es? Formato: dd:MM:yyyy HH:mm (ej. 20:09:2026 18:00)"));
  }

  private void manejarFecha(long chatId, TelegramSesionEntity sesion, String texto)
  {
    LocalDateTime fecha;
    try
    {
      fecha = LocalDateTime.parse(texto, FORMATO_FECHA);
    } catch (DateTimeParseException e)
    {
      telegramBot.execute(new SendMessage(chatId,
          "No entendi esa fecha. Formato: dd:MM:yyyy HH:mm (ej. 20:09:2026 18:00)"));
      return;
    }

    if (!fecha.isAfter(LocalDateTime.now()))
    {
      telegramBot.execute(new SendMessage(chatId,
          "La fecha tiene que ser futura. ¿Cuando es? Formato: dd:MM:yyyy HH:mm"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("fecha", fecha.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_DURACION);

    telegramBot.execute(new SendMessage(chatId, "¿Cuantas horas dura aproximadamente?"));
  }

  private void manejarDuracion(long chatId, TelegramSesionEntity sesion, String texto)
  {
    Integer duracion = TelegramFlujoUtils.parsearEnteroPositivo(texto);
    if (duracion == null)
    {
      telegramBot.execute(new SendMessage(chatId, "Mandame un numero entero mayor a 0. ¿Cuantas horas dura?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("duracionEstimada", String.valueOf(duracion));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_CUPO_MINIMO);

    telegramBot.execute(new SendMessage(chatId, "¿Cual es la cantidad minima de participantes? (al menos 2)"));
  }

  private void manejarCupoMinimo(long chatId, TelegramSesionEntity sesion, String texto)
  {
    Integer cupo = TelegramFlujoUtils.parsearEnteroPositivo(texto);
    if (cupo == null || cupo < 2)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero de al menos 2. ¿Cual es la cantidad minima?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("cantidadMinima", String.valueOf(cupo));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_CUPO_MAXIMO);

    telegramBot.execute(new SendMessage(chatId, "¿Y la cantidad maxima?"));
  }

  private void manejarCupoMaximo(long chatId, TelegramSesionEntity sesion, String texto, String usuarioId)
  {
    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    if (!datos.keySet().containsAll(CLAVES_PREVIAS_AL_CUPO_MAXIMO))
    {
      // datosParciales incompleto o corrupto: no hay con que armar la actividad.
      TelegramFlujoUtils.reiniciarSesionCorrupta(log, telegramBot, sesionRepository, chatId,
          "datosParciales incompleto " + datos.keySet(), "/crear");
      return;
    }
    int cupoMinimo = Integer.parseInt(datos.get("cantidadMinima"));

    Integer cupoMaximo = TelegramFlujoUtils.parsearEnteroPositivo(texto);
    if (cupoMaximo == null || cupoMaximo < 2)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero de al menos 2. ¿Cual es la cantidad maxima?"));
      return;
    }
    if (cupoMaximo < cupoMinimo)
    {
      telegramBot.execute(new SendMessage(chatId,
          "La cantidad maxima no puede ser menor a la minima (" + cupoMinimo + "). ¿Cual es la cantidad maxima?"));
      return;
    }

    crearActividad(chatId, datos, cupoMaximo, usuarioId);
  }

  /**
   * Arma el ActividadPostDto y crea la actividad. Todo el bloque (incluido el
   * armado, que parsea los datos guardados como texto) esta dentro del try:
   * pase lo que pase se avisa al usuario y se limpia la sesion, para que el
   * chat no quede colgado en el ultimo paso.
   */
  private void crearActividad(long chatId, Map<String, String> datos, int cupoMaximo, String usuarioId)
  {
    try
    {
      ActividadPostDto dto = armarDto(datos, cupoMaximo);
      ActividadDto creada = actividadesService.createActividad(dto, usuarioId);
      sesionRepository.deleteById(chatId);
      log.info("[Telegram] Actividad creada por chatId={}: actividadId={}", chatId, creada.id());
      telegramBot.execute(new SendMessage(chatId,
          "Listo, cree la actividad \"" + creada.titulo() + "\" (id " + creada.id() + ")."));
    } catch (Exception e)
    {
      log.warn("[Telegram] Fallo creando actividad para chatId={}: {}", chatId, e.getMessage());
      sesionRepository.deleteById(chatId);
      telegramBot.execute(new SendMessage(chatId,
          "No pude crear la actividad (" + e.getMessage() + "). Mandá /crear para intentar de nuevo."));
    }
  }

  private ActividadPostDto armarDto(Map<String, String> datos, int cupoMaximo)
  {
    return new ActividadPostDto(
        datos.get("titulo"),
        null,
        TipoActividad.valueOf(datos.get("tipoActividad")),
        new UbicacionDto(datos.get("ciudad"), null, null),
        LocalDateTime.parse(datos.get("fecha")),
        Integer.parseInt(datos.get("duracionEstimada")),
        Integer.parseInt(datos.get("cantidadMinima")),
        cupoMaximo);
  }

}
