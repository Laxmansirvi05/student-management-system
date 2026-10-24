package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.dto.FlowchartNode;
import com.campuscompanion.ai.parse.FlowchartRenderer;
import com.campuscompanion.ai.parse.FlowchartRenderer.Flowchart;
import com.campuscompanion.ai.parse.FlowchartRendererTestAccess;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** FR-AI-006: which flowchart graphs are accepted, and what Java builds from them. */
class FlowchartRendererTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static JsonNode graph(String nodes, String edges) {
    return JSON.readTree(
        "{\"title\":\"Binary search\",\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}");
  }

  private static final String NODES =
      "{\"id\":\"n1\",\"label\":\"Start\",\"type\":\"START\"},"
          + "{\"id\":\"n2\",\"label\":\"Is the middle \\\"x\\\" < key?\",\"type\":\"DECISION\"},"
          + "{\"id\":\"n3\",\"label\":\"Search the right half\",\"type\":\"STEP\"},"
          + "{\"id\":\"n4\",\"label\":\"Found\",\"type\":\"END\"}";
  private static final String EDGES =
      "{\"from\":\"n1\",\"to\":\"n2\",\"label\":\"\"},"
          + "{\"from\":\"n2\",\"to\":\"n3\",\"label\":\"Yes\"},"
          + "{\"from\":\"n2\",\"to\":\"n4\",\"label\":\"No\"},"
          + "{\"from\":\"n3\",\"to\":\"n2\"}";

  @Test
  void aSoundGraphIsKeptInReadingOrderWithStepsAndMermaidBuiltByJava() {
    Flowchart chart = FlowchartRenderer.from(graph(NODES, EDGES));

    assertThat(chart.title()).isEqualTo("Binary search");
    assertThat(chart.nodes()).extracting(FlowchartNode::id).containsExactly("n1", "n2", "n3", "n4");
    assertThat(chart.edges().get(0).label()).isNull();
    assertThat(chart.steps())
        .containsExactly(
            "Start",
            "Is the middle \"x\" < key? (Yes: go to step 3; No: go to step 4)",
            "Search the right half",
            "Found");
    // Java's own ids and escaped labels: nothing the model wrote reaches the syntax.
    assertThat(chart.mermaid())
        .startsWith("flowchart TD\n")
        .contains("N1([\"Start\"])")
        .contains("N2{\"Is the middle #quot;x#quot; #lt; key?\"}")
        .contains("N2 -->|\"Yes\"| N3")
        .contains("N3 --> N2")
        .doesNotContain("n1", "\"x\"", "<");
  }

  @Test
  void escapingCoversEveryCharacterWithAMeaningInMermaid() {
    String escaped = FlowchartRendererTestAccess.escape("a\"b<c>d|e#f{g}h[i]j(k)l");
    assertThat(escaped).doesNotContain("\"", "<", ">", "|", "{", "}", "[", "]", "(", ")");
  }

  @Test
  void brokenGraphsAreRefused() {
    String tooMany =
        java.util.stream.IntStream.rangeClosed(1, 21)
            .mapToObj(
                i ->
                    "{\"id\":\"n"
                        + i
                        + "\",\"label\":\"x\",\"type\":\""
                        + (i == 1 ? "START" : "END")
                        + "\"}")
            .collect(java.util.stream.Collectors.joining(","));
    String[][] broken = {
      {tooMany, EDGES},
      {NODES.replace("START", "STEP"), EDGES},
      {NODES.replace("\"type\":\"END\"", "\"type\":\"START\""), EDGES},
      {NODES.replace("END", "STEP"), EDGES},
      {NODES, EDGES.replace("\"to\":\"n4\"", "\"to\":\"n9\"")},
      {NODES, EDGES.replace("{\"from\":\"n3\",\"to\":\"n2\"}", "{\"from\":\"n1\",\"to\":\"n3\"}")},
      {NODES, EDGES + ",{\"from\":\"n4\",\"to\":\"n1\"}"},
      {NODES + ",{\"id\":\"n5\",\"label\":\"Alone\",\"type\":\"END\"}", EDGES},
      {NODES.replace("\"id\":\"n3\"", "\"id\":\"3 bad\""), EDGES},
      {NODES.replace("Search the right half", "x".repeat(81)), EDGES},
      {NODES.replace("\"type\":\"STEP\"", "\"type\":\"LOOP\""), EDGES},
      {NODES.replace("\"label\":\"Found\"", "\"label\":7"), EDGES},
      {NODES.replace("\"id\":\"n3\"", "\"id\":\"n2\""), EDGES},
    };
    for (String[] graph : broken) {
      assertThatThrownBy(() -> FlowchartRenderer.from(graph(graph[0], graph[1])))
          .as(graph[0] + " | " + graph[1])
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(
            () ->
                FlowchartRenderer.from(
                    JSON.readTree("{\"title\":\"t\",\"nodes\":[],\"edges\":[]}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                FlowchartRenderer.from(
                    JSON.readTree("{\"nodes\":[" + NODES + "],\"edges\":[" + EDGES + "]}")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
