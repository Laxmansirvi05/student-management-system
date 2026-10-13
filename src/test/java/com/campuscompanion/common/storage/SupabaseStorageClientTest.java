package com.campuscompanion.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.campuscompanion.common.config.StorageProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The Supabase Storage calls, against a fake server. Nothing here reaches the network: the real
 * bucket is checked by hand, as described in docs/17-testing-strategy.md section 8.
 */
class SupabaseStorageClientTest {

  private static final String BASE = "https://project.supabase.example/storage/v1";
  private static final Instant NOW = Instant.parse("2026-10-10T08:30:00Z");

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final SupabaseStorageClient storage =
      new SupabaseStorageClient(
          new StorageProperties("https://project.supabase.example/", "service-key", "learning"),
          builder,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void uploadsToThePrivateBucketWithTheServiceKeyAndWithoutOverwriting() {
    server
        .expect(requestTo(BASE + "/object/learning/resources/CS301/a.pdf"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer service-key"))
        .andExpect(header("x-upsert", "false"))
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andRespond(withSuccess());

    storage.upload("resources/CS301/a.pdf", new byte[] {1, 2, 3}, "application/pdf");

    server.verify();
  }

  @Test
  void deletesTheObject() {
    server
        .expect(requestTo(BASE + "/object/learning/resources/CS301/a.pdf"))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("Authorization", "Bearer service-key"))
        .andRespond(withSuccess());

    storage.delete("resources/CS301/a.pdf");

    server.verify();
  }

  @Test
  void signsALinkForTheRequestedTime() {
    server
        .expect(requestTo(BASE + "/object/sign/learning/resources/CS301/a.pdf"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"expiresIn\":300}"))
        .andRespond(
            withSuccess(
                "{\"signedURL\":\"/object/sign/learning/resources/CS301/a.pdf?token=abc\"}",
                MediaType.APPLICATION_JSON));

    StorageClient.SignedLink link =
        storage.signedUrl("resources/CS301/a.pdf", Duration.ofMinutes(5), null);

    assertThat(link.url())
        .isEqualTo(BASE + "/object/sign/learning/resources/CS301/a.pdf?token=abc");
    assertThat(link.expiresAt()).isEqualTo(NOW.plusSeconds(300));
  }

  @Test
  void aDownloadLinkCarriesTheFileNameSafelyEncoded() {
    server
        .expect(requestTo(BASE + "/object/sign/learning/x.pdf"))
        .andRespond(
            withSuccess(
                "{\"signedURL\":\"/object/sign/learning/x.pdf?token=abc\"}",
                MediaType.APPLICATION_JSON));

    StorageClient.SignedLink link =
        storage.signedUrl("x.pdf", Duration.ofMinutes(5), "Unit 1 & 2 notes.pdf");

    assertThat(link.url())
        .isEqualTo(
            BASE + "/object/sign/learning/x.pdf?token=abc&download=Unit%201%20%26%202%20notes.pdf");
  }

  @Test
  void aFailingStorageBecomesStorageUnavailable() {
    server.expect(requestTo(BASE + "/object/learning/x.pdf")).andRespond(withServerError());
    server.expect(requestTo(BASE + "/object/sign/learning/x.pdf")).andRespond(withServerError());

    assertThatThrownBy(() -> storage.upload("x.pdf", new byte[] {1}, "application/pdf"))
        .isInstanceOf(StorageUnavailableException.class);
    assertThatThrownBy(() -> storage.signedUrl("x.pdf", Duration.ofMinutes(5), null))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void anAnswerWithoutALinkBecomesStorageUnavailable() {
    server
        .expect(requestTo(BASE + "/object/sign/learning/x.pdf"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> storage.signedUrl("x.pdf", Duration.ofMinutes(5), null))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void withoutSettingsNothingIsCalled() {
    SupabaseStorageClient unset =
        new SupabaseStorageClient(
            new StorageProperties("", "", "learning"), builder, Clock.systemUTC());

    assertThatThrownBy(() -> unset.upload("x.pdf", new byte[] {1}, "application/pdf"))
        .isInstanceOf(StorageUnavailableException.class);
    assertThatThrownBy(() -> unset.signedUrl("x.pdf", Duration.ofMinutes(5), null))
        .isInstanceOf(StorageUnavailableException.class);
    assertThatThrownBy(() -> unset.delete("x.pdf")).isInstanceOf(StorageUnavailableException.class);
    server.verify();
  }
}
