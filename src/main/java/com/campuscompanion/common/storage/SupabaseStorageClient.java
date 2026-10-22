package com.campuscompanion.common.storage;

import com.campuscompanion.common.config.StorageProperties;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Supabase Storage over HTTPS, using the service key. The key is a server-side secret and never
 * reaches the browser. The bucket is private: the only way to a file is a signed link from here.
 *
 * <p>Supabase Storage REST API: upload and delete at /storage/v1/object/{bucket}/{path}, and a
 * signed link from /storage/v1/object/sign/{bucket}/{path}.
 */
@Component
public class SupabaseStorageClient implements StorageClient {

  private static final Logger log = LoggerFactory.getLogger(SupabaseStorageClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private final StorageProperties properties;
  private final RestClient http;
  private final Clock clock;

  @Autowired
  public SupabaseStorageClient(StorageProperties properties, Clock clock) {
    this(properties, builderWithTimeouts(), clock);
  }

  /** For tests, which pass a builder bound to a fake server. */
  SupabaseStorageClient(
      StorageProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
    this.properties = properties;
    this.http = restClientBuilder.build();
    this.clock = clock;
  }

  /** Storage that does not answer must not keep a request waiting for ever. */
  private static RestClient.Builder builderWithTimeouts() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().requestFactory(factory);
  }

  @Override
  public void upload(String path, byte[] content, String contentType) {
    call(
        "upload",
        () ->
            http.post()
                .uri(objectUrl(path))
                .headers(this::authenticate)
                .header("x-upsert", "false")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content)
                .retrieve()
                .toBodilessEntity());
  }

  @Override
  public void delete(String path) {
    call(
        "delete",
        () ->
            http.delete()
                .uri(objectUrl(path))
                .headers(this::authenticate)
                .retrieve()
                .toBodilessEntity());
  }

  @Override
  public SignedLink signedUrl(String path, Duration validFor, String downloadName) {
    requireConfigured();
    try {
      Map<?, ?> body =
          http.post()
              .uri(baseUrl() + "/object/sign/" + properties.bucket() + "/" + path)
              .headers(this::authenticate)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("expiresIn", validFor.toSeconds()))
              .retrieve()
              .body(Map.class);
      Object signed = body == null ? null : body.get("signedURL");
      if (!(signed instanceof String relative) || relative.isBlank()) {
        throw new StorageUnavailableException();
      }
      return new SignedLink(
          baseUrl() + relative + downloadParameter(downloadName), clock.instant().plus(validFor));
    } catch (RestClientException failure) {
      log.error("File storage failed during sign: {}", failure.getClass().getSimpleName());
      throw new StorageUnavailableException();
    }
  }

  /**
   * Supabase sends the file as an attachment with this name when the link carries "download". The
   * signed link already has a query string (its token), so the parameter is added with "&".
   */
  private static String downloadParameter(String downloadName) {
    if (downloadName == null) {
      return "";
    }
    return "&download="
        + URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Supabase's newer secret keys (sb_secret_...) are not JWTs and are accepted only in the apikey
   * header; the older service_role key is a JWT and works in either. Sending both suits both.
   */
  private void authenticate(HttpHeaders headers) {
    headers.set("apikey", properties.serviceKey());
    headers.setBearerAuth(properties.serviceKey());
  }

  private void call(String action, Runnable request) {
    requireConfigured();
    try {
      request.run();
    } catch (RestClientException failure) {
      // The message of the exception can contain the URL, so only its type is logged.
      log.error("File storage failed during {}: {}", action, failure.getClass().getSimpleName());
      throw new StorageUnavailableException();
    }
  }

  private void requireConfigured() {
    if (!StringUtils.hasText(properties.supabaseUrl())
        || !StringUtils.hasText(properties.serviceKey())
        || !StringUtils.hasText(properties.bucket())) {
      log.warn("File storage is not set up: SUPABASE_URL and SUPABASE_SERVICE_KEY are needed.");
      throw new StorageUnavailableException();
    }
  }

  private String baseUrl() {
    return properties.supabaseUrl().replaceAll("/+$", "") + "/storage/v1";
  }

  private String objectUrl(String path) {
    return baseUrl() + "/object/" + properties.bucket() + "/" + path;
  }
}
