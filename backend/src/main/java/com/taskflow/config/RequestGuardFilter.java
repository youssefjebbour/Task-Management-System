package com.taskflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.common.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Origin protection for cookie endpoints and bounded per-IP throttling for authentication. */
public class RequestGuardFilter extends OncePerRequestFilter {
  private final Set<String> origins;
  private final ObjectMapper mapper;
  private final int authLimit;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  private record Bucket(long window, int count) {}

  public RequestGuardFilter(Set<String> origins, ObjectMapper mapper, int authLimit) {
    this.origins = origins;
    this.mapper = mapper;
    this.authLimit = authLimit;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    boolean auth =
        req.getRequestURI().startsWith("/api/v1/auth/")
            && !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod());
    if (auth) {
      String origin = req.getHeader("Origin");
      if (origin != null && !origins.contains(origin)) {
        reject(res, 403, "Origin is not allowed");
        return;
      }
      if ("cross-site".equals(req.getHeader("Sec-Fetch-Site"))) {
        reject(res, 403, "Cross-site authentication request rejected");
        return;
      }
      long window = System.currentTimeMillis() / 60000;
      if (buckets.size() > 10000) buckets.entrySet().removeIf(e -> e.getValue().window() < window);
      if (buckets.size() > 20000) {
        reject(res, 429, "Please try again shortly");
        return;
      }
      String key = req.getRemoteAddr() + ":" + req.getRequestURI();
      Bucket value =
          buckets.compute(
              key,
              (k, b) ->
                  b == null || b.window() != window
                      ? new Bucket(window, 1)
                      : new Bucket(window, b.count() + 1));
      int limit = req.getRequestURI().endsWith("refresh") ? 120 : authLimit;
      if (value.count() > limit) {
        res.setHeader("Retry-After", "60");
        reject(res, 429, "Too many attempts. Try again in a minute.");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  private void reject(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    mapper.writeValue(response.getOutputStream(), ApiError.of(status, message));
  }
}
