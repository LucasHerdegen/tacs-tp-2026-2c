package com.tacs.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/hello")
class BasicController
{
  @GetMapping
  public ResponseEntity<String> hello()
  {
    return ResponseEntity.ok("I'm up!");
  }
}
