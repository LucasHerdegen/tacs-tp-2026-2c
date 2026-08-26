package com.tacs.backend.dtos.auth;

public record LoginResponse(String token, String tokenType, long expiresIn)
{
}
