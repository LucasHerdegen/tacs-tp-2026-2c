package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.tacs.backend.persistence.entities.TelegramUpdateOffsetEntity;
import com.tacs.backend.persistence.repositories.TelegramUpdateOffsetMongoRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Long polling de la Bot API: pregunta por updates nuevos desde el ultimo
 * offset persistido, los despacha uno por uno, y actualiza el offset. Una
 * falla despachando un update puntual no interrumpe el resto del lote (mismo
 * criterio que ChequeoClimaJob/CierreVotacionJob).
 */
@Slf4j
@Component
public class TelegramUpdatePoller
{
  private final TelegramBot telegramBot;
  private final TelegramResilience telegramResilience;
  private final TelegramUpdateOffsetMongoRepository offsetRepository;
  private final ComandoRouter comandoRouter;
  private final TextoLibreRouter textoLibreRouter;
  private final CallbackRouter callbackRouter;
  private final int timeoutSeconds;

  public TelegramUpdatePoller(
      TelegramBot telegramBot,
      TelegramResilience telegramResilience,
      TelegramUpdateOffsetMongoRepository offsetRepository,
      ComandoRouter comandoRouter,
      TextoLibreRouter textoLibreRouter,
      CallbackRouter callbackRouter,
      @Value("${telegram.polling.timeout-seconds}") int timeoutSeconds)
  {
    this.telegramBot = telegramBot;
    this.telegramResilience = telegramResilience;
    this.offsetRepository = offsetRepository;
    this.comandoRouter = comandoRouter;
    this.textoLibreRouter = textoLibreRouter;
    this.callbackRouter = callbackRouter;
    this.timeoutSeconds = timeoutSeconds;
  }

  // fixedDelay (no fixedRate): getUpdates ya bloquea hasta timeout-seconds
  // cuando no hay novedades, no tiene sentido superponer corridas.
  @Scheduled(fixedDelayString = "${telegram.polling.delay-ms}")
  @SchedulerLock(name = "TelegramUpdatePoller_procesarUpdates", lockAtLeastFor = "500ms", lockAtMostFor = "1m")
  public void procesarUpdates()
  {
    int offset = obtenerOffset();

    GetUpdatesResponse response = telegramResilience.ejecutar(() ->
        telegramBot.execute(new GetUpdates().offset(offset).timeout(timeoutSeconds)));

    int nuevoOffset = offset;
    for (Update update : response.updates())
    {
      despacharSinRomperElLoop(update);
      nuevoOffset = update.updateId() + 1;
    }

    if (nuevoOffset != offset)
      guardarOffset(nuevoOffset);
  }

  private int obtenerOffset()
  {
    return offsetRepository.findById(TelegramUpdateOffsetEntity.ID_UNICO)
        .map(entity -> (int) entity.getUltimoUpdateId())
        .orElse(0);
  }

  private void guardarOffset(int nuevoOffset)
  {
    offsetRepository.save(new TelegramUpdateOffsetEntity(nuevoOffset));
  }

  private void despacharSinRomperElLoop(Update update)
  {
    try
    {
      despachar(update);
    } catch (Exception e)
    {
      log.error("Fallo procesando el update_id={} de Telegram, se continua con el resto del lote",
          update.updateId(), e);
    }
  }

  private void despachar(Update update)
  {
    if (update.callbackQuery() != null)
    {
      callbackRouter.despachar(update.callbackQuery());
      return;
    }

    Message mensaje = update.message();
    if (mensaje == null || mensaje.text() == null)
      return;

    if (mensaje.text().startsWith("/"))
      comandoRouter.despachar(mensaje);
    else
      textoLibreRouter.despachar(mensaje);
  }
}
