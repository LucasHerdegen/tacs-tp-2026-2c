package com.tacs.backend.exceptions;

/**
 * Error no reintentable al enviar un mensaje por Telegram (ej. 403: el
 * usuario bloqueo al bot o desactivo su cuenta). A diferencia de un timeout
 * o un 5xx, reintentar esto no tiene sentido: el estado no va a cambiar
 * entre un intento y el siguiente dentro de la misma corrida.
 */
public class TelegramEnvioPermanenteException extends RuntimeException
{
  public TelegramEnvioPermanenteException(String message)
  {
    super(message);
  }
}
