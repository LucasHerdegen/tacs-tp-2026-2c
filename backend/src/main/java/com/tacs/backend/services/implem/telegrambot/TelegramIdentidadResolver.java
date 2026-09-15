package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resuelve la identidad interna (Usuario) de un chat de Telegram a partir de
 * su chatId, buscando por MedioContacto. No hay JWT en este canal: esta
 * resolucion hace las veces de "autenticacion" para todos los handlers del bot.
 */
@Component
@RequiredArgsConstructor
public class TelegramIdentidadResolver
{
  private final UsuarioRepository usuarioRepository;

  public Optional<String> resolverUsuarioId(long chatId)
  {
    MedioContacto medioContacto = new MedioContacto(String.valueOf(chatId), TipoMedioContacto.TELEGRAM);
    return usuarioRepository.findByMedioContacto(medioContacto).map(Usuario::getId);
  }
}
