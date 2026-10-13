package com.campuscompanion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuscompanion.common.config.AcademicProperties;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.config.IdentityProperties;
import com.campuscompanion.common.web.RequestIdFilter;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Task T1-02: the application starts, connects as the runtime role and exposes only health. */
@AutoConfigureMockMvc
class ApplicationIT extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private IdentityProperties identityProperties;
  @Autowired private AcademicProperties academicProperties;
  @Autowired private AiProperties aiProperties;

  @Test
  void applicationConnectsAsTheRuntimeRole() {
    String currentUser = jdbcTemplate.queryForObject("select current_user", String.class);

    assertThat(currentUser).isEqualTo(APP_USER);
  }

  @Test
  void healthIsPublicAndUpAndShowsNoDetails() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(header().exists(RequestIdFilter.HEADER));
  }

  @Test
  void noOtherActuatorEndpointIsReachable() throws Exception {
    for (String path :
        new String[] {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/info"}) {
      int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

      assertThat(status).as(path).isIn(401, 403, 404);
    }
  }

  @Test
  void everythingElseIsDeniedToAVisitor() throws Exception {
    int status = mockMvc.perform(get("/api/v1/anything")).andReturn().getResponse().getStatus();

    assertThat(status).isIn(401, 403);
  }

  @Test
  void settingsAreBoundWithTheirDefaults() {
    assertThat(identityProperties.studentEmailDomain()).isEqualTo("university.example");
    assertThat(academicProperties.attendanceRequiredPercent())
        .isEqualByComparingTo(new BigDecimal("75"));
    assertThat(aiProperties.provider()).isEqualTo("mock");
    assertThat(aiProperties.dailyLimitPerUser()).isEqualTo(30);
    assertThat(aiProperties.dailyLimitGlobal()).isEqualTo(500);
  }
}
