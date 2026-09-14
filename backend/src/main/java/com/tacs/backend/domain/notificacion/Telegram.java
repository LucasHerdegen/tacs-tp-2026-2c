package com.tacs.backend.domain.notificacion;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.services.implem.telegrambot.TelegramResilience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Telegram implements Notificador
{
  private final TelegramBot telegramBot;
  private final TelegramResilience telegramResilience;

  @Override
  public boolean soporta(TipoMedioContacto tipo)
  {
    return tipo == TipoMedioContacto.TELEGRAM;
  }

  @Override
  public void enviarNotificacion(String contenido, MedioContacto destinatario)
  {
    long chatId = Long.parseLong(destinatario.getValor());
    telegramResilience.ejecutar(() -> telegramBot.execute(new SendMessage(chatId, contenido)));
  }
}
