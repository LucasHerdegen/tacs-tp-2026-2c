package com.tacs.backend;

import com.mongodb.client.MongoClient;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
class BackendApplicationTests
{
  @MockitoBean
  private MongoClient mongoClient;

  @MockitoBean
  private LockProvider lockProvider;

  @Test
  void contextLoads()
  {
  }

}
