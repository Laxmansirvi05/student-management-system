package com.campuscompanion.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuscompanion.common.web.RequestIdFilter;
import errorprobe.ProbeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Task T1-05: every error leaves the API as a problem detail with the documented status and code, a
 * trace identifier, and nothing internal. A small probe controller raises each kind of failure.
 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyErrorCodeIsReturnedWithItsDocumentedStatus(ErrorCode code) throws Exception {
    mockMvc
        .perform(get("/probe/api/{code}", code.name()))
        .andExpect(status().is(code.status().value()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(code.status().value()))
        .andExpect(jsonPath("$.code").value(code.name()))
        .andExpect(jsonPath("$.title").value(code.title()))
        .andExpect(jsonPath("$.detail").value("Shown to the user."))
        .andExpect(jsonPath("$.instance").value("/probe/api/" + code.name()))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void notFoundAndConflictUseTheirOwnExceptions() throws Exception {
    mockMvc
        .perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("We could not find that."));
    mockMvc
        .perform(get("/probe/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.detail").value("A student with this roll number already exists."));
  }

  @Test
  void anInvalidBodyListsEachInvalidField() throws Exception {
    mockMvc
        .perform(
            post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"code\":\"TOO-LONG-CODE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.detail").value("One or more fields are invalid."))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].field").value("code"))
        .andExpect(jsonPath("$.errors[1].field").value("name"))
        .andExpect(jsonPath("$.errors[1].message").isNotEmpty());
  }

  @Test
  void anInvalidParameterIsReportedAsAFieldError() throws Exception {
    mockMvc
        .perform(get("/probe/constraint"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }

  @Test
  void unreadableJsonAndWrongParameterTypesAreBadRequestsWithAPlainMessage() throws Exception {
    MvcResult malformed =
        mockMvc
            .perform(
                post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andReturn();
    MvcResult wrongType =
        mockMvc
            .perform(get("/probe/number").param("value", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andReturn();

    assertNothingInternal(malformed);
    assertNothingInternal(wrongType);
  }

  @Test
  void anUnsupportedMethodIsRejectedWithoutInternals() throws Exception {
    MvcResult result =
        mockMvc
            .perform(delete("/probe/not-found"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andReturn();

    assertNothingInternal(result);
  }

  @Test
  void anUnexpectedFailureReturnsAReferenceAndHidesEverythingElse() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(
                jsonPath("$.detail").value("Something went wrong on our side. Please try again."))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andReturn();

    assertNothingInternal(result);
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(ProbeController.INTERNAL_MESSAGE);
  }

  @Test
  void theTraceIdInTheBodyMatchesTheResponseHeader() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/not-found")).andReturn();

    String header = result.getResponse().getHeader(RequestIdFilter.HEADER);

    assertThat(header).hasSize(12);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"traceId\":\"" + header + "\"");
  }

  private static void assertNothingInternal(MvcResult result) throws Exception {
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("Exception")
        .doesNotContain("com.campuscompanion")
        .doesNotContain("org.springframework")
        .doesNotContain("java.lang")
        .doesNotContain("\tat ");
  }
}
