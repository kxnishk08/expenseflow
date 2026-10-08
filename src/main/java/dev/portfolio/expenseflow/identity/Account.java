package dev.portfolio.expenseflow.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class Account {
  @Id public UUID id;
  public String email;

  @Column(name = "display_name")
  public String displayName;

  @Column(name = "password_hash")
  public String passwordHash;

  public String role;
  public boolean active;

  @Column(name = "manager_id")
  public UUID managerId;

  @Column(name = "created_at")
  public Instant createdAt;

  protected Account() {}

  public Account(
      UUID id, String email, String name, String hash, String role, UUID manager, Instant now) {
    this.id = id;
    this.email = email;
    displayName = name;
    passwordHash = hash;
    this.role = role;
    managerId = manager;
    active = true;
    createdAt = now;
  }
}
