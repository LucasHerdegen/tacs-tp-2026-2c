package com.tacs.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimeConfig
{
  private final String timezoneId;

  public TimeConfig(@Value("${app.timezone:America/Argentina/Buenos_Aires}") String timezoneId)
  {
    this.timezoneId = timezoneId;
  }

  @PostConstruct
  public void init()
  {
    TimeZone.setDefault(TimeZone.getTimeZone(timezoneId));
  }
}

