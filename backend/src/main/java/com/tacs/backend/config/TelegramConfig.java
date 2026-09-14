package com.tacs.backend.config;

import com.pengrad.telegrambot.TelegramBot;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Arma el {@link TelegramBot} (cliente de la Bot API, libreria Pengrad) usado
 * tanto para el envio de notificaciones ({@code Telegram implements Notificador})
 * como para el polling de updates de la interfaz interactiva, con timeouts
 * configurables por properties (mismo criterio que {@code WeatherApiConfig}).
 */
@Configuration
public class TelegramConfig
{
  @Bean
  public TelegramBot telegramBot(
      @Value("${telegram.bot-token}") String botToken,
      @Value("${telegram.connect-timeout-ms}") long connectTimeoutMs,
      @Value("${telegram.read-timeout-ms}") long readTimeoutMs)
  {
    OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .build();

    return new TelegramBot.Builder(botToken)
        .okHttpClient(httpClient)
        .build();
  }
}
