package com.campuscompanion.ai.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The adapter for providers that speak the widely copied "chat completions" format: Groq,
 * OpenRouter and Ollama's cloud service. One class serves all three; only the address, the key and
 * the model differ. It makes one call and returns the model's text. The key is sent only in the
 * Authorization header and is never logged. Nothing about any person is added: only the
 * instructions and the student's text.
 */
public class OpenAiCompatibleAiProvider implements AiProvider {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final HttpClient http;

  /**
   * @param baseUrl the provider's API root, for example {@code https://api.groq.com/openai/v1}
   */
  public OpenAiCompatibleAiProvider(
      String baseUrl, String apiKey, String model, Duration connectTimeout) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  @Override
  public AiCompletion complete(AiRequest request) throws AiProviderException {
    HttpRequest call =
        HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
            .timeout(request.timeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", ProviderHttp.USER_AGENT)
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body(request), StandardCharsets.UTF_8))
            .build();
    String text = textOf(ProviderHttp.send(http, call));
    return new AiCompletion(text, request.userContent().length(), text.length());
  }

  /** The model this adapter asks, for logs and route names. */
  public String model() {
    return model;
  }

  private String body(AiRequest request) {
    ObjectNode root = JSON.createObjectNode();
    root.put("model", model);
    ArrayNode messages = root.putArray("messages");
    messages.addObject().put("role", "system").put("content", request.systemInstructions());
    messages.addObject().put("role", "user").put("content", request.userContent());
    root.put("max_tokens", request.maxOutputTokens());
    root.put("temperature", 0.4);
    return root.toString();
  }

  /** The text of the first answer, or "" when there is none (the caller then refuses it). */
  private static String textOf(String json) throws AiProviderException {
    JsonNode answer;
    try {
      answer = JSON.readTree(json);
    } catch (RuntimeException notJson) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    // Some providers report a failure inside a 200 answer.
    if (answer.has("error")) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    JsonNode content = answer.path("choices").path(0).path("message").path("content");
    return content.isString() ? content.asString() : "";
  }
}
