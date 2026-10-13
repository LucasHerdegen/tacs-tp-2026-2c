package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resuelve la identidad interna (Usuario) de una persona de Telegram a partir
 * de su remitenteId (Message.from().id() / CallbackQuery.from().id()),
 * buscando por MedioContacto. No hay JWT en este canal: esta resolucion hace
 * las veces de "autenticacion" para todos los handlers del bot.
 * <p>
 * Deliberadamente NO se usa el chatId: identifica la conversacion, no a la
 * persona, y en un grupo es el mismo numero para todos los miembros.
 */
@Component
@RequiredArgsConstructor
public class TelegramIdentidadResolver
{
  private final UsuarioRepository usuarioRepository;

  public Optional<String> resolverUsuarioId(long remitenteId)
  {
    MedioContacto medioContacto = new MedioContacto(String.valueOf(remitenteId), TipoMedioContacto.TELEGRAM);
    return usuarioRepository.findByMedioContacto(medioContacto).map(Usuario::getId);
  }
}
