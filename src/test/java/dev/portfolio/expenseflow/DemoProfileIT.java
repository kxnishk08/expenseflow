package dev.portfolio.expenseflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "DEMO_PROVISION=false",
      "DEMO_ALICE_PASSWORD=disposable-demo-password",
      "DEMO_M1_PASSWORD=disposable-demo-password",
      "DEMO_F1_PASSWORD=disposable-demo-password"
    })
@AutoConfigureMockMvc
@ActiveProfiles("public-demo")
class DemoProfileIT extends DatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired Environment env;

  @Test
  void anonymousVisitorCanLoadDemoButProtectedApiStillRequiresLogin() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/demo.js")).andExpect(status().isOk());
    mvc.perform(get("/demo.html")).andExpect(status().isOk());
    mvc.perform(get("/app.js")).andExpect(status().isOk());
    mvc.perform(get("/app.css")).andExpect(status().isOk());
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login")).andExpect(status().isForbidden());
  }

  @Test
  void onlyDisposableCredentialsArePublishedAndCookiesRemainSecure() throws Exception {
    var response =
        mvc.perform(get("/demo/config"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response)
        .contains("alice@example.test", "m1@example.test")
        .doesNotContain("DB_PASSWORD", "DB_URL", "bob@example.test");
    assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
    assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("3");
  }
}
