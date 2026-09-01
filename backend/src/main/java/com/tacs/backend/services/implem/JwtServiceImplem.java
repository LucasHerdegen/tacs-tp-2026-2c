package com.tacs.backend.services.implem;

import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.services.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
class JwtServiceImplem implements JwtService
{
  private static final String ISSUER = "https://tacs-api";

  private final JwtEncoder jwtEncoder;
  private final long expirationSeconds;

  JwtServiceImplem(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.expiration-seconds}") long expirationSeconds)
  {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");

    SecretKey secretKey = new SecretKeySpec(
        secret.getBytes(StandardCharsets.UTF_8),
        "HmacSHA256");

    this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
        .algorithm(MacAlgorithm.HS256)
        .build();
    this.expirationSeconds = expirationSeconds;
  }

  @Override
  public String generarToken(Usuario usuario)
  {
    Instant issuedAt = Instant.now();

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject(usuario.getUsername())
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plusSeconds(expirationSeconds))
        .claim("role", usuario.getRol().name())
        .claim("id", usuario.getId())
        .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public long getExpirationSeconds()
  {
    return expirationSeconds;
  }
}
