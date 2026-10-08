package dev.portfolio.expenseflow.identity;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  @GetMapping("/api/auth/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "token",
        token.getToken(),
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName());
  }

  @GetMapping("/api/me")
  Map<String, Object> me(@AuthenticationPrincipal Actor actor) {
    return actor.profile();
  }
}
