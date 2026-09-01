package com.tacs.backend.domain.notificacion;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import org.springframework.stereotype.Component;

@Component
public class Telegram implements Notificador
{
  @Override
  public boolean soporta(TipoMedioContacto tipo)
  {
    return tipo == TipoMedioContacto.TELEGRAM;
  }

  @Override
  public void enviarNotificacion(String contenido, MedioContacto destinatario)
  {
    // TODO: Implementar envío real vía Telegram API
    System.out.println("Enviando notificación vía Telegram a " + destinatario.getValor() + ": " + contenido);
  }
}
