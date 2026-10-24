package com.campuscompanion.ai.parse;

import com.campuscompanion.ai.dto.FlowchartEdge;
import com.campuscompanion.ai.dto.FlowchartNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Turns the model's flowchart graph into one the application can trust (FR-AI-006, docs/12 section
 * 8), as plain Java. The graph is checked: at most 20 nodes with safe ids, exactly one START, at
 * least one END, no edge to an unknown node, every non-END node leads somewhere, and every node can
 * be reached from START. Java then builds the step list and the Mermaid text itself, with its own
 * node ids and escaped labels: the model never writes diagram syntax.
 *
 * <p>A graph that fails a check is rejected with {@link IllegalArgumentException}.
 */
public final class FlowchartRenderer {

  static final int MAX_NODES = 20;
  static final int MAX_EDGES = 40;
  static final int MAX_LABEL = 80;
  static final int MAX_EDGE_LABEL = 30;
  private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,19}$");
  private static final Set<String> TYPES = Set.of("START", "END", "STEP", "DECISION");

  /** A checked flowchart. */
  public record Flowchart(
      String title,
      List<FlowchartNode> nodes,
      List<FlowchartEdge> edges,
      List<String> steps,
      String mermaid) {}

  private FlowchartRenderer() {}

  public static Flowchart from(JsonNode reply) {
    String title = text(reply.get("title"), MAX_LABEL, false);
    JsonNode nodeList = reply.get("nodes");
    JsonNode edgeList = reply.get("edges");
    if (nodeList == null
        || !nodeList.isArray()
        || nodeList.size() < 2
        || nodeList.size() > MAX_NODES) {
      throw new IllegalArgumentException("nodes");
    }
    if (edgeList == null
        || !edgeList.isArray()
        || edgeList.isEmpty()
        || edgeList.size() > MAX_EDGES) {
      throw new IllegalArgumentException("edges");
    }

    Map<String, FlowchartNode> nodes = new LinkedHashMap<>();
    for (JsonNode entry : nodeList) {
      String id = text(entry.get("id"), 20, false);
      String type = text(entry.get("type"), 10, false);
      if (!ID.matcher(id).matches() || !TYPES.contains(type) || nodes.containsKey(id)) {
        throw new IllegalArgumentException("node");
      }
      nodes.put(id, new FlowchartNode(id, text(entry.get("label"), MAX_LABEL, false), type));
    }
    long starts = nodes.values().stream().filter(node -> node.type().equals("START")).count();
    boolean anyEnd = nodes.values().stream().anyMatch(node -> node.type().equals("END"));
    if (starts != 1 || !anyEnd) {
      throw new IllegalArgumentException("start or end");
    }

    List<FlowchartEdge> edges = new ArrayList<>();
    Map<String, List<FlowchartEdge>> outgoing = new HashMap<>();
    for (JsonNode entry : edgeList) {
      String from = text(entry.get("from"), 20, false);
      String to = text(entry.get("to"), 20, false);
      if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
        throw new IllegalArgumentException("edge");
      }
      String label = text(entry.get("label"), MAX_EDGE_LABEL, true);
      FlowchartEdge edge = new FlowchartEdge(from, to, label);
      edges.add(edge);
      outgoing.computeIfAbsent(from, key -> new ArrayList<>()).add(edge);
    }
    for (FlowchartNode node : nodes.values()) {
      boolean leads = outgoing.containsKey(node.id());
      if (node.type().equals("END") ? leads : !leads) {
        throw new IllegalArgumentException("dangling");
      }
    }

    List<String> order = reachableFromStart(nodes, outgoing);
    if (order.size() != nodes.size()) {
      throw new IllegalArgumentException("unreachable");
    }
    List<FlowchartNode> ordered = order.stream().map(nodes::get).toList();
    return new Flowchart(
        title, ordered, edges, steps(ordered, nodes, outgoing), mermaid(ordered, edges));
  }

  private static List<String> reachableFromStart(
      Map<String, FlowchartNode> nodes, Map<String, List<FlowchartEdge>> outgoing) {
    String start =
        nodes.values().stream()
            .filter(node -> node.type().equals("START"))
            .findFirst()
            .orElseThrow()
            .id();
    List<String> order = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>(List.of(start));
    seen.add(start);
    while (!queue.isEmpty()) {
      String id = queue.poll();
      order.add(id);
      for (FlowchartEdge edge : outgoing.getOrDefault(id, List.of())) {
        if (seen.add(edge.to())) {
          queue.add(edge.to());
        }
      }
    }
    return order;
  }

  /** The chart as numbered text, in the order it is read; decisions name where each answer goes. */
  private static List<String> steps(
      List<FlowchartNode> ordered,
      Map<String, FlowchartNode> nodes,
      Map<String, List<FlowchartEdge>> outgoing) {
    Map<String, Integer> number = new HashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      number.put(ordered.get(i).id(), i + 1);
    }
    List<String> steps = new ArrayList<>();
    for (FlowchartNode node : ordered) {
      StringBuilder line = new StringBuilder(node.label());
      List<FlowchartEdge> next = outgoing.getOrDefault(node.id(), List.of());
      if (node.type().equals("DECISION") || next.size() > 1) {
        List<String> branches = new ArrayList<>();
        for (FlowchartEdge edge : next) {
          String answer = edge.label() == null ? "Then" : edge.label();
          branches.add(answer + ": go to step " + number.get(edge.to()));
        }
        line.append(" (").append(String.join("; ", branches)).append(")");
      }
      steps.add(line.toString());
    }
    return steps;
  }

  /** Mermaid text built here, with our own ids and every label escaped. */
  private static String mermaid(List<FlowchartNode> ordered, List<FlowchartEdge> edges) {
    Map<String, String> ids = new HashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      ids.put(ordered.get(i).id(), "N" + (i + 1));
    }
    StringBuilder text = new StringBuilder("flowchart TD\n");
    for (FlowchartNode node : ordered) {
      String label = "\"" + escape(node.label()) + "\"";
      String shape =
          switch (node.type()) {
            case "START", "END" -> "([" + label + "])";
            case "DECISION" -> "{" + label + "}";
            default -> "[" + label + "]";
          };
      text.append("  ").append(ids.get(node.id())).append(shape).append('\n');
    }
    for (FlowchartEdge edge : edges) {
      text.append("  ").append(ids.get(edge.from())).append(" -->");
      if (edge.label() != null) {
        text.append("|\"").append(escape(edge.label())).append("\"|");
      }
      text.append(' ').append(ids.get(edge.to())).append('\n');
    }
    return text.toString();
  }

  /** Characters with a meaning in Mermaid labels, as Mermaid entity codes. */
  static String escape(String label) {
    StringBuilder out = new StringBuilder();
    for (char c : label.toCharArray()) {
      switch (c) {
        case '"' -> out.append("#quot;");
        case '<' -> out.append("#lt;");
        case '>' -> out.append("#gt;");
        case '|' -> out.append("#124;");
        case '#' -> out.append("#35;");
        case '{', '}', '[', ']', '(', ')' -> out.append("#").append((int) c).append(';');
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  /** Plain text of at most {@code limit} characters, control characters removed. */
  private static String text(JsonNode node, int limit, boolean optional) {
    String value = node == null || node.isNull() ? null : node.isString() ? node.asString() : null;
    if (value == null) {
      if (optional) {
        return null;
      }
      throw new IllegalArgumentException("missing text");
    }
    String cleaned = value.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
    if (cleaned.isEmpty()) {
      if (optional) {
        return null;
      }
      throw new IllegalArgumentException("blank text");
    }
    if (cleaned.length() > limit) {
      throw new IllegalArgumentException("too long");
    }
    return cleaned;
  }
}
