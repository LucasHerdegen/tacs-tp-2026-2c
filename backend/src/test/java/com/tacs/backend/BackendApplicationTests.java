package com.tacs.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
class BackendApplicationTests
{

  @Test
  void contextLoads()
  {
  }

}
