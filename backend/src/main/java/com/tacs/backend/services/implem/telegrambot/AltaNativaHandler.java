package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Alta de un Usuario nuevo directamente desde el bot, sin contrasenia
 * elegida por la persona (no es objetivo del TP trabajar sobre login/registro
 * clasico para este canal). El chatId nace ya vinculado como MedioContacto.
 */
@Component
@RequiredArgsConstructor
public class AltaNativaHandler
{
  private final AuthService authService;

  public UsuarioDto crearUsuarioNativo(long chatId)
  {
    RegistroRequest registro = new RegistroRequest("tg_" + chatId, UUID.randomUUID().toString());
    UsuarioDto usuario = authService.registrar(registro);

    return authService.actualizarContacto(usuario.id(),
        new MedioContacto(String.valueOf(chatId), TipoMedioContacto.TELEGRAM));
  }
}
