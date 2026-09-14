package com.tacs.backend.exceptions;

/**
 * Error potencialmente transitorio al enviar un mensaje por Telegram (ej.
 * timeout, 5xx del lado de Telegram). Reintentable por resilience4j, a
 * diferencia de {@link TelegramEnvioPermanenteException}.
 */
public class TelegramEnvioTransitorioException extends RuntimeException
{
  public TelegramEnvioTransitorioException(String message)
  {
    super(message);
  }

  public TelegramEnvioTransitorioException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
