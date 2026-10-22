package com.campuscompanion.ai.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The adapter for Google's Gemini models, used when AI_PROVIDER is "gemini". It makes one call to
 * the generateContent method and returns the model's text. The key comes from AI_API_KEY, is sent
 * only in a request header, and is never logged. Nothing about any person is added to a request:
 * only the instructions and the student's text.
 *
 * <p>A failure becomes an {@link AiProviderException} that carries only its kind, because the
 * provider's own message can repeat the student's text.
 */
public class GeminiAiProvider implements AiProvider {

  public static final String DEFAULT_MODEL = "gemini-flash-latest";
  static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String apiKey;
  private final String model;
  private final String baseUrl;
  private final HttpClient http;

  public GeminiAiProvider(String apiKey, String model, Duration connectTimeout) {
    this(apiKey, model, connectTimeout, DEFAULT_BASE_URL);
  }

  /** For tests in other packages, which point it at a server of their own. */
  public static GeminiAiProvider forTest(
      String apiKey, String model, Duration connectTimeout, String baseUrl) {
    return new GeminiAiProvider(apiKey, model, connectTimeout, baseUrl);
  }

  private GeminiAiProvider(String apiKey, String model, Duration connectTimeout, String baseUrl) {
    this.apiKey = apiKey;
    this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    this.baseUrl = baseUrl;
    this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  @Override
  public AiCompletion complete(AiRequest request) throws AiProviderException {
    HttpRequest call =
        HttpRequest.newBuilder(
                URI.create(
                    baseUrl
                        + "/v1beta/models/"
                        + URLEncoder.encode(model, StandardCharsets.UTF_8)
                        + ":generateContent"))
            .timeout(request.timeout())
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body(request), StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> response;
    try {
      response = http.send(call, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException timeout) {
      throw new AiProviderException(AiProviderException.Kind.TIMEOUT);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    } catch (IOException unreachable) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    int status = response.statusCode();
    if (status == 429) {
      throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED);
    }
    if (status >= 500) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    if (status >= 400) {
      // A wrong key, a wrong model name or a refused request: the application is misconfigured.
      throw new AiProviderException(AiProviderException.Kind.BAD_REQUEST);
    }
    String text = textOf(response.body());
    return new AiCompletion(text, request.userContent().length(), text.length());
  }

  private static String body(AiRequest request) {
    ObjectNode root = JSON.createObjectNode();
    root.putObject("systemInstruction")
        .putArray("parts")
        .addObject()
        .put("text", request.systemInstructions());
    ObjectNode turn = root.putArray("contents").addObject();
    turn.put("role", "user");
    turn.putArray("parts").addObject().put("text", request.userContent());
    ObjectNode config = root.putObject("generationConfig");
    config.put("maxOutputTokens", request.maxOutputTokens());
    config.put("temperature", 0.4);
    return root.toString();
  }

  /** The text of the first answer, or "" when the model gave none (for example a blocked one). */
  private static String textOf(String json) throws AiProviderException {
    JsonNode answer;
    try {
      answer = JSON.readTree(json);
    } catch (RuntimeException notJson) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    JsonNode parts = answer.path("candidates").path(0).path("content").path("parts");
    if (!(parts instanceof ArrayNode list)) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode part : list) {
      // A "thought" part is the model's own reasoning, not part of the answer.
      if (!part.path("thought").asBoolean(false) && part.path("text").isString()) {
        text.append(part.path("text").asString());
      }
    }
    return text.toString();
  }
}
