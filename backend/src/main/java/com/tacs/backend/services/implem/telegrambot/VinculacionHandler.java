package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.TelegramVinculacionInvalidaException;
import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import com.tacs.backend.persistence.repositories.TelegramVinculacionMongoRepository;
import com.tacs.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Resuelve el flujo de "/start <token>": asocia el chatId a un Usuario ya
 * registrado por REST, usando un token de un solo uso generado por
 * TelegramVinculacionService.
 */
@Component
@RequiredArgsConstructor
public class VinculacionHandler
{
  private final TelegramVinculacionMongoRepository vinculacionRepository;
  private final AuthService authService;

  public UsuarioDto vincular(long chatId, String token)
  {
    TelegramVinculacionEntity vinculacion = vinculacionRepository.findByToken(token)
        .orElseThrow(() -> new TelegramVinculacionInvalidaException(
            "Ese link de vinculacion no es valido. Generá uno nuevo desde la app."));

    if (vinculacion.isUsado())
      throw new TelegramVinculacionInvalidaException(
          "Ese link de vinculacion ya fue utilizado. Generá uno nuevo desde la app.");

    if (vinculacion.getExpiracion().isBefore(LocalDateTime.now()))
      throw new TelegramVinculacionInvalidaException(
          "Ese link de vinculacion vencio. Generá uno nuevo desde la app.");

    UsuarioDto usuario = authService.actualizarContacto(vinculacion.getUsuarioId(),
        new MedioContacto(String.valueOf(chatId), TipoMedioContacto.TELEGRAM));

    vinculacion.setUsado(true);
    vinculacionRepository.save(vinculacion);

    return usuario;
  }
}
