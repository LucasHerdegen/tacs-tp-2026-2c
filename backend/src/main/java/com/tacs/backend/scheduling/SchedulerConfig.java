package com.tacs.backend.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.mongodb.client.MongoClient;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig
{
  @Bean
  public LockProvider lockProvider(MongoClient mongoClient)
  {
    return new MongoLockProvider(mongoClient.getDatabase("tacs"));
  }
}
