package com.tacs.backend.domain.notificacion;

import com.tacs.backend.domain.usuario.MedioContacto;
import org.springframework.stereotype.Component;

@Component
public class Telegram implements Notificador
{
  @Override
  public void enviarNotificacion(String contenido, MedioContacto destinatario)
  {
    // TODO: Implementar envío real vía Telegram API
    System.out.println("Enviando notificación vía Telegram a " + destinatario.getValor() + ": " + contenido);
  }
}
