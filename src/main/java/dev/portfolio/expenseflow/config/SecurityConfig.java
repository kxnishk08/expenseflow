package dev.portfolio.expenseflow.config;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService accounts(AccountRepository repo) {
    return email ->
        repo.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .map(a -> new Actor(a.id, a.email, a.displayName, a.passwordHash, a.role, a.active))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  private static void error(
      ObjectMapper json,
      HttpServletRequest r,
      HttpServletResponse s,
      int status,
      String code,
      String message)
      throws IOException {
    s.setStatus(status);
    s.setContentType("application/json");
    json.writeValue(s.getOutputStream(), new ApiError(code, message, RequestIds.id(r), List.of()));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
    return http.csrf(c -> c.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(
            c ->
                c.requestMatchers("/api/auth/csrf", "/api/auth/login", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/manager/**")
                    .hasRole("MANAGER")
                    .requestMatchers("/api/finance/**")
                    .hasRole("FINANCE")
                    .requestMatchers("/api/reports/**")
                    .hasRole("EMPLOYEE")
                    .requestMatchers("/actuator/**")
                    .hasRole("FINANCE")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            c ->
                c.loginProcessingUrl("/api/auth/login")
                    .successHandler((r, s, a) -> s.setStatus(204))
                    .failureHandler(
                        (r, s, e) ->
                            error(json, r, s, 401, "INVALID_CREDENTIALS", "Invalid credentials.")))
        .logout(
            c ->
                c.logoutUrl("/api/auth/logout")
                    .logoutRequestMatcher(
                        r ->
                            r.getMethod().equals("POST")
                                && r.getRequestURI().equals(r.getContextPath() + "/api/auth/logout")
                                && org.springframework.security.core.context.SecurityContextHolder
                                        .getContext()
                                        .getAuthentication()
                                    != null
                                && org.springframework.security.core.context.SecurityContextHolder
                                        .getContext()
                                        .getAuthentication()
                                        .getPrincipal()
                                    instanceof Actor)
                    .deleteCookies("EXPENSEFLOWSESSION")
                    .logoutSuccessHandler((r, s, a) -> s.setStatus(204)))
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (r, s, e) ->
                            error(json, r, s, 401, "UNAUTHENTICATED", "Authentication required."))
                    .accessDeniedHandler(
                        (r, s, e) ->
                            error(
                                json,
                                r,
                                s,
                                403,
                                "FORBIDDEN",
                                "Operation forbidden or CSRF validation failed.")))
        .build();
  }
}
