package dev.portfolio.expenseflow.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIds extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestIds.class);

  public static String id(HttpServletRequest request) {
    return (String) request.getAttribute("requestId");
  }

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-Id");
    String id =
        supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    request.setAttribute("requestId", id);
    response.setHeader("X-Request-Id", id);
    MDC.put("requestId", id);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "requestId={} method={} status={} durationMs={}",
          id,
          request.getMethod(),
          response.getStatus(),
          (System.nanoTime() - start) / 1000000);
      MDC.remove("requestId");
    }
  }
}
