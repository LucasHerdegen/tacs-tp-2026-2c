package com.tacs.backend.services.implem.telegrambot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidades compartidas por los FlujoHandler basados en TelegramSesion
 * (CrearActividadFlujoHandler, ConfigurarClimaFlujoHandler: manejo del JSON de datosParciales, avance de paso, y
 * parseo de numeros con la misma regla de "invalido -> null, se repregunta"
 * en todos los wizards. Clase estatica sin estado, no es un bean de Spring.
 */
final class TelegramFlujoUtils
{
  private TelegramFlujoUtils()
  {
  }

  static Map<String, String> leerDatos(ObjectMapper objectMapper, TelegramSesionEntity sesion)
  {
    try
    {
      return objectMapper.readValue(sesion.getDatosParciales(), new TypeReference<Map<String, String>>()
      {
      });
    } catch (JsonProcessingException e)
    {
      return new HashMap<>();
    }
  }

  static void guardarDatos(ObjectMapper objectMapper, TelegramSesionEntity sesion, Map<String, String> datos)
  {
    try
    {
      sesion.setDatosParciales(objectMapper.writeValueAsString(datos));
    } catch (JsonProcessingException e)
    {
      throw new IllegalStateException("No se pudieron serializar los datos parciales de la sesion", e);
    }
  }

  static void avanzarA(TelegramSesionMongoRepository sesionRepository, TelegramSesionEntity sesion, String paso)
  {
    sesion.setPasoActual(paso);
    sesion.setActualizadoEn(LocalDateTime.now());
    sesionRepository.save(sesion);
  }

  /**
   * Borra la TelegramSesion y avisa al usuario que algo salio mal, indicando
   * con que comando puede arrancar de nuevo (ej. "/crear" o "/clima"). Se usa
   * tanto para un pasoActual desconocido como para un datosParciales
   * incompleto/corrupto — en ambos casos no hay con que seguir el flujo.
   */
  static void reiniciarSesionCorrupta(Logger log, TelegramBot telegramBot,
      TelegramSesionMongoRepository sesionRepository, long chatId, String motivo, String comandoParaReiniciar)
  {
    log.warn("[Telegram] TelegramSesion inconsistente para chatId={}: {}", chatId, motivo);
    sesionRepository.deleteById(chatId);
    telegramBot.execute(new SendMessage(chatId, "Algo salio mal. Mandá " + comandoParaReiniciar
        + " para empezar de nuevo."));
  }

  static Integer parsearEnteroPositivo(String texto)
  {
    try
    {
      int valor = Integer.parseInt(texto.trim());
      return valor > 0 ? valor : null;
    } catch (NumberFormatException e)
    {
      return null;
    }
  }

  static Integer parsearEnteroNoNegativo(String texto)
  {
    try
    {
      int valor = Integer.parseInt(texto.trim());
      return valor >= 0 ? valor : null;
    } catch (NumberFormatException e)
    {
      return null;
    }
  }

  static Integer parsearHora(String texto)
  {
    try
    {
      int valor = Integer.parseInt(texto.trim());
      return valor >= 0 && valor <= 23 ? valor : null;
    } catch (NumberFormatException e)
    {
      return null;
    }
  }

  static Double parsearDecimal(String texto)
  {
    try
    {
      return Double.parseDouble(texto.trim().replace(",", "."));
    } catch (NumberFormatException e)
    {
      return null;
    }
  }
}
