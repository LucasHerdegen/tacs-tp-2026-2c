package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.dtos.usuario.TelegramVinculacionDto;
import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import com.tacs.backend.persistence.repositories.TelegramVinculacionMongoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Genera el link de vinculacion (deep-link "/start <token>") que un usuario
 * ya logueado por JWT pide desde la app para asociar su cuenta a un chat de
 * Telegram. Invalida cualquier token previo sin usar antes de crear uno
 * nuevo, para que solo el ultimo link generado sea valido.
 */
@Component
public class TelegramVinculacionService
{
  private final TelegramVinculacionMongoRepository vinculacionRepository;
  private final String botUsername;
  private final long expiracionMinutos;

  public TelegramVinculacionService(
      TelegramVinculacionMongoRepository vinculacionRepository,
      @Value("${telegram.bot-username}") String botUsername,
      @Value("${telegram.vinculacion.expiracion-minutos}") long expiracionMinutos)
  {
    this.vinculacionRepository = vinculacionRepository;
    this.botUsername = botUsername;
    this.expiracionMinutos = expiracionMinutos;
  }

  public TelegramVinculacionDto generarVinculacion(String usuarioId)
  {
    invalidarTokensPrevios(usuarioId);

    String token = UUID.randomUUID().toString();
    LocalDateTime expiracion = LocalDateTime.now().plusMinutes(expiracionMinutos);
    vinculacionRepository.save(new TelegramVinculacionEntity(token, usuarioId, expiracion));

    String deepLink = "https://t.me/" + botUsername + "?start=" + token;
    return new TelegramVinculacionDto(token, deepLink, expiracion);
  }

  private void invalidarTokensPrevios(String usuarioId)
  {
    List<TelegramVinculacionEntity> previos = vinculacionRepository.findByUsuarioIdAndUsadoFalse(usuarioId);
    previos.forEach(v -> v.setUsado(true));
    vinculacionRepository.saveAll(previos);
  }
}
