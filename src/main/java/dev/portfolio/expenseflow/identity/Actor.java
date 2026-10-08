package dev.portfolio.expenseflow.identity;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record Actor(
    UUID id, String email, String displayName, String passwordHash, String role, boolean active)
    implements UserDetails {
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  public String getPassword() {
    return passwordHash;
  }

  public String getUsername() {
    return email;
  }

  public boolean isEnabled() {
    return active;
  }

  public Map<String, Object> profile() {
    return Map.of("id", id, "email", email, "displayName", displayName, "role", role);
  }
}
