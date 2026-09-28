package com.tacs.backend.services.implem.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.dtos.clima.ReglasClimaDto;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import com.tacs.backend.services.ActividadesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.DoublePredicate;

/**
 * Wizard de "/clima": pregunta paso a paso las reglas de clima, la
 * anticipacion y el rango de reprogramacion de una actividad. Las reglas de
 * clima aceptan "-" para omitirlas; lo demas es obligatorio porque sin rango
 * de reprogramacion no se puede abrir una votacion automatica despues.
 */
@Slf4j
@Component
public class ConfigurarClimaFlujoHandler implements FlujoHandler
{
  public static final String FLUJO = "CONFIGURAR_CLIMA";
  public static final String PREFIJO_CALLBACK_RANGO = "config_clima_rango";

  static final String PASO_LLUVIA = "LLUVIA";
  static final String PASO_TEMP_MINIMA = "TEMP_MINIMA";
  static final String PASO_TEMP_MAXIMA = "TEMP_MAXIMA";
  static final String PASO_VIENTO = "VIENTO";
  static final String PASO_ANTICIPACION = "ANTICIPACION";
  static final String PASO_RANGO_PREGUNTA = "RANGO_PREGUNTA";
  static final String PASO_RANGO_DIAS = "RANGO_DIAS";
  static final String PASO_RANGO_HORA_INICIO = "RANGO_HORA_INICIO";
  static final String PASO_RANGO_HORA_FIN = "RANGO_HORA_FIN";

  private static final String OMITIR = "-";

  // Claves que ya tienen que estar guardadas cuando se llega al ultimo paso
  // del bloque de reprogramacion (rango permitido).
  private static final List<String> CLAVES_PREVIAS_AL_RANGO_HORA_FIN =
      List.of("actividadId", "horasAnticipacion", "rangoDias", "rangoHoraInicio");

  // Claves minimas para configurar el clima sin reprogramacion automatica.
  private static final List<String> CLAVES_PREVIAS_SIN_RANGO = List.of("actividadId", "horasAnticipacion");

  private final TelegramBot telegramBot;
  private final TelegramSesionMongoRepository sesionRepository;
  private final ActividadesService actividadesService;
  private final int maxDiasForecast;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ConfigurarClimaFlujoHandler(TelegramBot telegramBot, TelegramSesionMongoRepository sesionRepository,
      ActividadesService actividadesService, @Value("${weatherapi.forecast.max-days}") int maxDiasForecast)
  {
    this.telegramBot = telegramBot;
    this.sesionRepository = sesionRepository;
    this.actividadesService = actividadesService;
    this.maxDiasForecast = maxDiasForecast;
  }

  @Override
  public String flujo()
  {
    return FLUJO;
  }

  /**
   * Invocado por SeleccionarActividadClimaCallbackHandler tras crear la
   * TelegramSesion, para mandar la primera pregunta del wizard.
   */
  void preguntarLluvia(long chatId)
  {
    telegramBot.execute(new SendMessage(chatId,
        "¿Cual es el porcentaje maximo de probabilidad de lluvia que tolera la actividad? "
            + "Mandá un numero (ej. 70) o \"-\" para no configurar esta regla."));
  }

  @Override
  public void manejar(Message mensaje, TelegramSesionEntity sesion, String usuarioId)
  {
    long chatId = sesion.getChatId();
    String texto = mensaje.text() == null ? "" : mensaje.text().trim();

    switch (sesion.getPasoActual())
    {
      case PASO_LLUVIA -> manejarDecimalOpcional(chatId, sesion, texto, "maxProbabilidadLluvia", PASO_TEMP_MINIMA,
          "¿Cual es la temperatura minima tolerada (en °C)? Mandá un numero o \"-\" para omitir.",
          esPorcentajeValido(), "Tiene que ser un porcentaje entre 0 y 100. ¿Cual es la probabilidad maxima de lluvia?");
      case PASO_TEMP_MINIMA -> manejarDecimalOpcional(chatId, sesion, texto, "minTemperatura", PASO_TEMP_MAXIMA,
          "¿Cual es la temperatura maxima tolerada (en °C)? Mandá un numero o \"-\" para omitir.",
          valor -> true, "");
      case PASO_TEMP_MAXIMA -> manejarTempMaxima(chatId, sesion, texto);
      case PASO_VIENTO -> manejarDecimalOpcional(chatId, sesion, texto, "maxViento", PASO_ANTICIPACION,
          "¿Con cuantas horas de anticipacion queres que se chequee el pronostico? (numero entero, minimo 1)",
          esNoNegativo(), "La velocidad de viento no puede ser negativa. ¿Cual es la velocidad maxima tolerada?");
      case PASO_ANTICIPACION -> manejarAnticipacion(chatId, sesion, texto);
      case PASO_RANGO_PREGUNTA -> telegramBot.execute(
          new SendMessage(chatId, "Elegi una opcion tocando uno de los botones de arriba."));
      case PASO_RANGO_DIAS -> manejarRangoDias(chatId, sesion, texto);
      case PASO_RANGO_HORA_INICIO -> manejarRangoHoraInicio(chatId, sesion, texto);
      case PASO_RANGO_HORA_FIN -> manejarRangoHoraFin(chatId, sesion, texto, usuarioId);
      default -> TelegramFlujoUtils.reiniciarSesionCorrupta(log, telegramBot, sesionRepository, chatId,
          "pasoActual desconocido '" + sesion.getPasoActual() + "'", "/clima");
    }
  }

  private void manejarDecimalOpcional(long chatId, TelegramSesionEntity sesion, String texto, String clave,
      String pasoSiguiente, String preguntaSiguiente, DoublePredicate esValido, String mensajeFueraDeRango)
  {
    if (!OMITIR.equals(texto))
    {
      Double valor = TelegramFlujoUtils.parsearDecimal(texto);
      if (valor == null)
      {
        telegramBot.execute(new SendMessage(chatId,
            "Mandame un numero, o \"-\" si no querés configurar esta regla."));
        return;
      }
      if (!esValido.test(valor))
      {
        telegramBot.execute(new SendMessage(chatId, mensajeFueraDeRango));
        return;
      }
      Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
      datos.put(clave, String.valueOf(valor));
      TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    }

    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, pasoSiguiente);
    telegramBot.execute(new SendMessage(chatId, preguntaSiguiente));
  }

  /**
   * PASO_TEMP_MAXIMA queda fuera de manejarDecimalOpcional: su validez no
   * depende solo del valor en si, sino de que no sea menor a la temperatura
   * minima ya guardada en un paso anterior (si se configuro).
   */
  private void manejarTempMaxima(long chatId, TelegramSesionEntity sesion, String texto)
  {
    if (!OMITIR.equals(texto))
    {
      Double valor = TelegramFlujoUtils.parsearDecimal(texto);
      if (valor == null)
      {
        telegramBot.execute(new SendMessage(chatId,
            "Mandame un numero, o \"-\" si no querés configurar esta regla."));
        return;
      }

      Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
      String minGuardada = datos.get("minTemperatura");
      if (minGuardada != null && valor < Double.parseDouble(minGuardada))
      {
        telegramBot.execute(new SendMessage(chatId,
            "La temperatura maxima no puede ser menor a la minima (" + minGuardada
                + "). ¿Cual es la temperatura maxima tolerada?"));
        return;
      }

      datos.put("maxTemperatura", String.valueOf(valor));
      TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    }

    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_VIENTO);
    telegramBot.execute(new SendMessage(chatId,
        "¿Cual es la velocidad maxima de viento tolerada (en km/h)? Mandá un numero o \"-\" para omitir."));
  }

  private DoublePredicate esPorcentajeValido()
  {
    return valor -> valor >= 0 && valor <= 100;
  }

  private DoublePredicate esNoNegativo()
  {
    return valor -> valor >= 0;
  }

  private void manejarAnticipacion(long chatId, TelegramSesionEntity sesion, String texto)
  {
    Integer horas = TelegramFlujoUtils.parsearEnteroPositivo(texto);
    int maxHorasAnticipacion = maxDiasForecast * 24;
    if (horas == null || horas > maxHorasAnticipacion)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero entre 1 y " + maxHorasAnticipacion + " (" + maxDiasForecast
              + " dias, el maximo que soporta el pronostico contratado). ¿Con cuantas horas de anticipacion se chequea el pronostico?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("horasAnticipacion", String.valueOf(horas));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_RANGO_PREGUNTA);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
        new InlineKeyboardButton("Si, permitir reprogramacion").callbackData(PREFIJO_CALLBACK_RANGO + ":SI"),
        new InlineKeyboardButton("No, cancelar directamente").callbackData(PREFIJO_CALLBACK_RANGO + ":NO"));

    telegramBot.execute(new SendMessage(chatId,
        "Si el clima resulta desfavorable, ¿querés que se busque una fecha alternativa y se abra una votacion? "
            + "Si elegis que no, la actividad se cancela directamente en ese caso.").replyMarkup(markup));
  }

  /**
   * Invocado por el CallbackHandler del boton Si/No de reprogramacion
   * automatica. Con "No" se salta directo al ultimo paso sin rango
   * configurado; con "Si" continua el bloque de preguntas de dias/horario.
   */
  public String manejarRespuestaRango(TelegramSesionEntity sesion, boolean permiteReprogramacion, String usuarioId)
  {
    if (!PASO_RANGO_PREGUNTA.equals(sesion.getPasoActual()))
      return "Esa opcion ya no es valida.";

    long chatId = sesion.getChatId();

    if (!permiteReprogramacion)
    {
      Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
      if (!datos.keySet().containsAll(CLAVES_PREVIAS_SIN_RANGO))
      {
        TelegramFlujoUtils.reiniciarSesionCorrupta(log, telegramBot, sesionRepository, chatId,
            "datosParciales incompleto " + datos.keySet(), "/clima");
        return "Algo salio mal.";
      }
      configurarClima(chatId, datos, usuarioId);
      return "Entendido, no se va a reprogramar automaticamente.";
    }

    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_RANGO_DIAS);
    telegramBot.execute(new SendMessage(chatId,
        "¿Cuantos dias de margen hay para buscar una fecha alternativa? "
            + "Mandá un numero entero (0 o mas), no una fecha (ej. 3 = busca en los 3 dias siguientes)."));
    return "Vamos a configurar la reprogramacion.";
  }

  private void manejarRangoDias(long chatId, TelegramSesionEntity sesion, String texto)
  {
    Integer dias = TelegramFlujoUtils.parsearEnteroNoNegativo(texto);
    if (dias == null || dias > maxDiasForecast)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero entre 0 y " + maxDiasForecast
              + " (el maximo que soporta el pronostico contratado), no una fecha. "
              + "¿Cuantos dias de margen hay para reprogramar?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("rangoDias", String.valueOf(dias));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_RANGO_HORA_INICIO);

    telegramBot.execute(new SendMessage(chatId,
        "¿A partir de que hora del dia se puede reprogramar? (0 a 23)"));
  }

  private void manejarRangoHoraInicio(long chatId, TelegramSesionEntity sesion, String texto)
  {
    Integer hora = TelegramFlujoUtils.parsearHora(texto);
    if (hora == null)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero entre 0 y 23. ¿A partir de que hora se puede reprogramar?"));
      return;
    }

    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    datos.put("rangoHoraInicio", String.valueOf(hora));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    TelegramFlujoUtils.avanzarA(sesionRepository, sesion, PASO_RANGO_HORA_FIN);

    telegramBot.execute(new SendMessage(chatId,
        "¿Hasta que hora del dia se puede reprogramar? (0 a 23, mayor a la hora de inicio)"));
  }

  private void manejarRangoHoraFin(long chatId, TelegramSesionEntity sesion, String texto, String usuarioId)
  {
    Map<String, String> datos = TelegramFlujoUtils.leerDatos(objectMapper, sesion);
    if (!datos.keySet().containsAll(CLAVES_PREVIAS_AL_RANGO_HORA_FIN))
    {
      TelegramFlujoUtils.reiniciarSesionCorrupta(log, telegramBot, sesionRepository, chatId,
          "datosParciales incompleto " + datos.keySet(), "/clima");
      return;
    }

    Integer horaFin = TelegramFlujoUtils.parsearHora(texto);
    if (horaFin == null)
    {
      telegramBot.execute(new SendMessage(chatId,
          "Tiene que ser un numero entero entre 0 y 23. ¿Hasta que hora se puede reprogramar?"));
      return;
    }
    int horaInicio = Integer.parseInt(datos.get("rangoHoraInicio"));
    if (horaFin <= horaInicio)
    {
      telegramBot.execute(new SendMessage(chatId,
          "La hora final tiene que ser mayor a la hora de inicio (" + horaInicio
              + "). ¿Hasta que hora se puede reprogramar?"));
      return;
    }

    datos.put("rangoHoraFin", String.valueOf(horaFin));
    TelegramFlujoUtils.guardarDatos(objectMapper, sesion, datos);
    configurarClima(chatId, datos, usuarioId);
  }

  /**
   * Arma el ConfigurarCondicionesDto y llama al Service. Todo el bloque
   * esta dentro del try: pase lo que pase se avisa al
   * usuario y se limpia la sesion, para que el chat no quede colgado.
   */
  private void configurarClima(long chatId, Map<String, String> datos, String usuarioId)
  {
    String actividadId = datos.get("actividadId");
    try
    {
      ConfigurarCondicionesDto dto = armarDto(datos);
      ActividadDto actualizada = actividadesService.actualizarConfiguracionClima(actividadId, usuarioId, dto);
      sesionRepository.deleteById(chatId);
      log.info("[Telegram] Configuracion de clima actualizada por chatId={}: actividadId={}", chatId, actividadId);
      telegramBot.execute(new SendMessage(chatId,
          "Listo, configuré el clima de \"" + actualizada.titulo() + "\"."));
    } catch (Exception e)
    {
      log.warn("[Telegram] Fallo configurando el clima de actividadId={} para chatId={}: {}",
          actividadId, chatId, e.getMessage());
      sesionRepository.deleteById(chatId);
      telegramBot.execute(new SendMessage(chatId,
          "No pude configurar el clima (" + e.getMessage() + "). Mandá /clima para intentar de nuevo."));
    }
  }

  private ConfigurarCondicionesDto armarDto(Map<String, String> datos)
  {
    ReglasClimaDto reglasClima = new ReglasClimaDto(
        parsearDecimalGuardado(datos.get("maxProbabilidadLluvia")),
        parsearDecimalGuardado(datos.get("minTemperatura")),
        parsearDecimalGuardado(datos.get("maxTemperatura")),
        parsearDecimalGuardado(datos.get("maxViento")));

    // Sin "rangoDias" guardado, el usuario elijio no permitir reprogramacion
    // automatica (boton "No"): se manda rango=null, ActividadesService no
    // toca el rango de la actividad.
    RangoReprogramacionDto rango = datos.containsKey("rangoDias")
        ? new RangoReprogramacionDto(
            Integer.parseInt(datos.get("rangoDias")),
            Integer.parseInt(datos.get("rangoHoraInicio")),
            Integer.parseInt(datos.get("rangoHoraFin")))
        : null;

    return new ConfigurarCondicionesDto(reglasClima, Integer.parseInt(datos.get("horasAnticipacion")), rango);
  }

  private Double parsearDecimalGuardado(String valor)
  {
    return valor == null ? null : Double.parseDouble(valor);
  }
}
