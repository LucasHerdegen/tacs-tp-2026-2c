package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Boton inline del listado de "/clima" (callback_data "config_clima:<id>").
 * Antes de arrancar el sub-wizard, vuelve a chequear que el id elegido sea
 * una de las actividades organizadas por el usuario.
 */
@Component
@RequiredArgsConstructor
public class SeleccionarActividadClimaCallbackHandler implements CallbackHandler
{
  public static final String PREFIJO = "config_clima";

  private final TelegramSesionMongoRepository sesionRepository;
  private final ActividadesService actividadesService;
  private final ConfigurarClimaFlujoHandler flujoHandler;

  @Override
  public String prefijo()
  {
    return PREFIJO;
  }

  @Override
  public String manejar(CallbackQuery callbackQuery, String usuarioId)
  {
    if (usuarioId == null)
      return "Esa opcion ya no es valida.";

    long chatId = callbackQuery.maybeInaccessibleMessage().chat().id();
    String actividadId = callbackQuery.data().split(":", 2)[1];

    boolean esOrganizador = actividadesService.actividadesOrganizadas(usuarioId, null).stream()
        .map(ActividadDto::id)
        .anyMatch(actividadId::equals);

    if (!esOrganizador)
      return "Esa opcion ya no es valida.";

    TelegramSesionEntity sesion = new TelegramSesionEntity(chatId, usuarioId);
    sesion.setFlujoActual(ConfigurarClimaFlujoHandler.FLUJO);
    sesion.setPasoActual(ConfigurarClimaFlujoHandler.PASO_LLUVIA);
    sesion.setDatosParciales("{\"actividadId\":\"" + actividadId + "\"}");
    sesionRepository.save(sesion);

    flujoHandler.preguntarLluvia(chatId);
    return "Vamos a configurar el clima.";
  }
}
