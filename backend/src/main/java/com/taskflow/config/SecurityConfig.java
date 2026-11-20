package com.taskflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.taskflow.common.ApiError;
import com.taskflow.user.*;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  Clock clock(@Value("${app.zone}") String zone) {
    return Clock.system(ZoneId.of(zone));
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String secret) {
    byte[] bytes = Base64.getDecoder().decode(secret);
    if (bytes.length < 32)
      throw new IllegalArgumentException(
          "JWT_SECRET must contain at least 32 random bytes, base64 encoded");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("taskflow"),
            new JwtClaimValidator<List<String>>(
                "aud", a -> a != null && a.contains("taskflow-api"))));
    return decoder;
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      UserRepository users,
      ObjectMapper mapper,
      @Value("${app.cors-origins}") String allowed,
      @Value("${app.auth-rate-limit:20}") int authLimit)
      throws Exception {
    var origins = Set.of(allowed.split(","));
    http.csrf(c -> c.disable())
        .cors(c -> c.configurationSource(cors(origins)))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/docs")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(
                        j ->
                            j.jwtAuthenticationConverter(
                                jwt -> {
                                  var u =
                                      users
                                          .findById(UUID.fromString(jwt.getSubject()))
                                          .orElseThrow(
                                              () ->
                                                  new OAuth2AuthenticationException(
                                                      "invalid_token"));
                                  Number version = jwt.getClaim("ver");
                                  if (!u.isActive()
                                      || version == null
                                      || version.intValue() != u.getTokenVersion())
                                    throw new OAuth2AuthenticationException("invalid_token");
                                  return new JwtAuthenticationToken(
                                      jwt,
                                      List.of(
                                          new SimpleGrantedAuthority(
                                              "ROLE_" + u.getSystemRole().name())),
                                      u.getId().toString());
                                }))
                    .authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(401, "Your session expired. Sign in again."));
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, x) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(), ApiError.of(401, "Sign in to continue"));
                        })
                    .accessDeniedHandler(
                        (req, res, x) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(), ApiError.of(403, "Access denied"));
                        }))
        .headers(
            h ->
                h.contentTypeOptions(c -> {})
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(
                        r ->
                            r.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(
            new RequestGuardFilter(origins, mapper, authLimit),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private CorsConfigurationSource cors(Set<String> origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(new ArrayList<>(origins));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
