package com.taskflow.auth;

import com.taskflow.user.User;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final long minutes;
  private final SecureRandom random = new SecureRandom();

  public TokenService(JwtEncoder encoder, @Value("${app.access-token-minutes}") long minutes) {
    this.encoder = encoder;
    this.minutes = minutes;
  }

  public long expiresIn() {
    return minutes * 60;
  }

  public String access(User user) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("taskflow")
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(expiresIn()))
            .audience(List.of("taskflow-api"))
            .claim("role", user.getSystemRole().name())
            .claim("ver", user.getTokenVersion())
            .id(UUID.randomUUID().toString())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public String opaque() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
