package com.campuscompanion.assistant.attachment;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Reads a .docx file into text in reading order: headings, paragraphs, numbered and bulleted lists
 * (with their numbers worked out, so "1." and "(a)" survive), tables (as Markdown tables),
 * equations (as plain text such as (a)/(b) or x^(2)), and the pictures placed in the document. A
 * .docx is a zip of XML files, which the JDK opens by itself, so no library is needed.
 *
 * <p>The file is treated as hostile: only the parts that are needed are read, each with a size
 * limit (a small zip can unpack to a huge file), and the XML is read with doctypes and external
 * entities switched off.
 */
@Component
public class DocxReader {

  static final int MAX_ENTRIES = 3000;
  static final int MAX_XML_BYTES = 20 * 1024 * 1024;
  static final int MAX_PICTURE_BYTES = 5 * 1024 * 1024;
  static final long MAX_TOTAL_BYTES = 60L * 1024 * 1024;
  static final int MAX_PICTURES = 6;
  static final int MAX_CHARS = 60_000;

  private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
  private static final String R =
      "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
  private static final String WP =
      "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
  private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
  private static final Pattern HEADING = Pattern.compile("(?i)^heading\\s*([1-9])$");

  /** What was read: the pieces, the raw bytes of the pictures (checked later), and a cut notice. */
  public record Content(List<Segment> segments, List<byte[]> pictures, boolean cut) {}

  /** Reads the file, or throws an error the student can read. */
  public Content read(byte[] file) {
    Map<String, byte[]> parts = unpack(file);
    byte[] body = parts.get("word/document.xml");
    if (body == null) {
      throw new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "That is not a Word (.docx) document.");
    }
    Document document = parse(body);
    Numbering numbering = Numbering.of(parts.get("word/numbering.xml"));
    Map<String, String> targets = relationships(parts.get("word/_rels/document.xml.rels"));
    return new Walk(document, numbering, targets, parts).run();
  }

  // --- the zip ---------------------------------------------------------------------------------

  private static Map<String, byte[]> unpack(byte[] file) {
    Map<String, byte[]> parts = new HashMap<>();
    long total = 0;
    int entries = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (++entries > MAX_ENTRIES) {
          throw tooBig();
        }
        String name = entry.getName();
        boolean xml =
            name.equals("word/document.xml")
                || name.equals("word/numbering.xml")
                || name.equals("word/_rels/document.xml.rels");
        boolean media = name.startsWith("word/media/");
        if (!xml && !media) {
          continue;
        }
        byte[] data = readLimited(zip, xml ? MAX_XML_BYTES : MAX_PICTURE_BYTES);
        if (data == null) {
          if (xml) {
            throw tooBig();
          }
          continue; // a picture that is too large is left out
        }
        total += data.length;
        if (total > MAX_TOTAL_BYTES) {
          throw tooBig();
        }
        parts.put(name, data);
      }
    } catch (IOException notAZip) {
      throw new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "That is not a Word (.docx) document.");
    }
    return parts;
  }

  /** The bytes of the current entry, or null when it holds more than the limit. */
  private static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[16 * 1024];
    int total = 0;
    for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
      total += read;
      if (total > limit) {
        return null;
      }
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private static ApiException tooBig() {
    return new ApiException(
        ErrorCode.UPLOAD_TOO_LARGE, "The document is too large to read once it is unpacked.");
  }

  // --- the XML ---------------------------------------------------------------------------------

  static Document parse(byte[] xml) {
    try {
      DocumentBuilder builder = factory().newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xml));
    } catch (ParserConfigurationException | SAXException | IOException broken) {
      throw new ApiException(
          ErrorCode.UPLOAD_INVALID_TYPE, "That Word document could not be read.");
    }
  }

  private static DocumentBuilderFactory factory() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  private static Map<String, String> relationships(byte[] xml) {
    Map<String, String> targets = new HashMap<>();
    if (xml == null) {
      return targets;
    }
    Document rels = parse(xml);
    var list = rels.getElementsByTagName("Relationship");
    for (int i = 0; i < list.getLength(); i++) {
      Element relation = (Element) list.item(i);
      targets.put(relation.getAttribute("Id"), relation.getAttribute("Target"));
    }
    return targets;
  }

  // --- the document ----------------------------------------------------------------------------

  /** One reading of a document: the state needed while walking it from top to bottom. */
  private static final class Walk {
    private final Document document;
    private final Numbering numbering;
    private final Map<String, String> targets;
    private final Map<String, byte[]> parts;
    private final List<Segment> segments = new ArrayList<>();
    private final List<byte[]> pictures = new ArrayList<>();
    private final Map<String, Integer> pictureOf = new HashMap<>();
    private final StringBuilder pending = new StringBuilder();

    /** Pictures found in the paragraph or table being read; they follow it in the output. */
    private final List<Segment> after = new ArrayList<>();

    private final Map<String, int[]> counters = new HashMap<>();
    private int chars;
    private boolean cut;

    Walk(
        Document document,
        Numbering numbering,
        Map<String, String> targets,
        Map<String, byte[]> parts) {
      this.document = document;
      this.numbering = numbering;
      this.targets = targets;
      this.parts = parts;
    }

    Content run() {
      Element body = first(document.getDocumentElement(), W, "body");
      if (body != null) {
        blocks(body);
      }
      flush();
      return new Content(segments, pictures, cut);
    }

    private void blocks(Element parent) {
      for (Node node = parent.getFirstChild(); node != null && !cut; node = node.getNextSibling()) {
        if (!(node instanceof Element element)) {
          continue;
        }
        if (is(element, W, "p")) {
          paragraph(element);
        } else if (is(element, W, "tbl")) {
          table(element);
        } else if (is(element, W, "sdt")) {
          Element content = first(element, W, "sdtContent");
          if (content != null) {
            blocks(content);
          }
        }
      }
    }

    // ---- paragraphs

    private void paragraph(Element p) {
      String text = inline(p, true).strip();
      Element properties = first(p, W, "pPr");
      String prefix = "";
      if (properties != null) {
        int heading = headingLevel(properties);
        if (heading > 0) {
          prefix = "#".repeat(Math.min(heading, 6)) + " ";
        } else {
          Element list = first(properties, W, "numPr");
          if (list != null && !text.isEmpty()) {
            prefix = listLabel(list);
          }
        }
      }
      if (!text.isEmpty()) {
        line(prefix + text);
      }
      emitPictures();
    }

    private int headingLevel(Element properties) {
      Element style = first(properties, W, "pStyle");
      if (style != null) {
        String name = attribute(style, W, "val");
        if (name.equalsIgnoreCase("Title")) {
          return 1;
        }
        Matcher match = HEADING.matcher(name.replace("-", " "));
        if (match.matches()) {
          return Integer.parseInt(match.group(1));
        }
      }
      Element outline = first(properties, W, "outlineLvl");
      if (outline != null) {
        try {
          return Integer.parseInt(attribute(outline, W, "val")) + 1;
        } catch (NumberFormatException ignored) {
          return 0;
        }
      }
      return 0;
    }

    private String listLabel(Element numPr) {
      Element idElement = first(numPr, W, "numId");
      Element levelElement = first(numPr, W, "ilvl");
      String id = idElement == null ? "" : attribute(idElement, W, "val");
      int level = 0;
      if (levelElement != null) {
        try {
          level = Math.max(0, Math.min(8, Integer.parseInt(attribute(levelElement, W, "val"))));
        } catch (NumberFormatException ignored) {
          level = 0;
        }
      }
      if (id.isEmpty() || id.equals("0")) {
        return "";
      }
      int[] count = counters.computeIfAbsent(id, key -> new int[9]);
      count[level]++;
      for (int deeper = level + 1; deeper < 9; deeper++) {
        count[deeper] = 0;
      }
      return "  ".repeat(level) + numbering.label(id, level, count) + " ";
    }

    // ---- the text inside a paragraph

    /** The text of a paragraph or part of one, with equations and pictures worked in. */
    private String inline(Element parent, boolean top) {
      StringBuilder text = new StringBuilder();
      for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (!(node instanceof Element child)) {
          continue;
        }
        String name = child.getLocalName();
        String space = child.getNamespaceURI();
        if (W.equals(space)) {
          switch (name) {
            case "t" -> text.append(child.getTextContent());
            case "tab" -> text.append(' ');
            case "br", "cr" -> text.append(' ');
            case "noBreakHyphen" -> text.append('-');
            case "r", "hyperlink", "smartTag", "ins", "fldSimple", "sdtContent", "sdt" ->
                text.append(inline(child, false));
            case "drawing", "pict", "object" -> picture(child, text);
            case "txbxContent" -> text.append(" [text box: ").append(boxed(child)).append("] ");
            default -> {
              // properties, bookmarks, deleted text and the like carry no text
            }
          }
        } else if (M.equals(space) && (name.equals("oMath") || name.equals("oMathPara"))) {
          text.append(" $").append(Omml.text(child)).append("$ ");
        } else if (MC.equals(space) && name.equals("AlternateContent")) {
          Element choice = first(child, MC, "Choice");
          Element use = choice != null ? choice : first(child, MC, "Fallback");
          if (use != null) {
            text.append(inline(use, false));
          }
        } else if (top || child.hasChildNodes()) {
          text.append(inline(child, false));
        }
      }
      return text.toString();
    }

    private String boxed(Element content) {
      StringBuilder text = new StringBuilder();
      for (Node node = content.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element p && is(p, W, "p")) {
          String line = inline(p, true).strip();
          if (!line.isEmpty()) {
            text.append(text.length() > 0 ? " / " : "").append(line);
          }
        }
      }
      return text.toString();
    }

    /** A drawing: the pictures in it become places in the text, and text boxes in it are read. */
    private void picture(Element drawing, StringBuilder text) {
      for (Element blip : all(drawing, A, "blip")) {
        String id = attribute(blip, R, "embed");
        byte[] data = media(id);
        if (data == null || pictures.size() >= MAX_PICTURES && !pictureOf.containsKey(id)) {
          continue;
        }
        Integer index = pictureOf.get(id);
        if (index == null) {
          index = pictures.size();
          pictures.add(data);
          pictureOf.put(id, index);
        }
        List<Element> described = all(drawing, WP, "docPr");
        Element properties = described.isEmpty() ? null : described.get(0);
        String alt = properties == null ? "" : properties.getAttribute("descr").strip();
        after.add(Segment.picture(alt.isEmpty() ? "" : "Picture description: " + alt, index));
      }
      for (Element box : all(drawing, null, "txbxContent")) {
        text.append(" [text box: ").append(boxed(box)).append("] ");
      }
    }

    private byte[] media(String id) {
      String target = targets.get(id);
      if (target == null) {
        return null;
      }
      String path = target.startsWith("/") ? target.substring(1) : "word/" + target;
      return parts.get(path.replace("../", ""));
    }

    // ---- tables

    private void table(Element table) {
      List<List<String>> rows = new ArrayList<>();
      int width = 0;
      for (Node node = table.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element row && is(row, W, "tr")) {
          List<String> cells = new ArrayList<>();
          for (Node cellNode = row.getFirstChild();
              cellNode != null;
              cellNode = cellNode.getNextSibling()) {
            if (cellNode instanceof Element cell && is(cell, W, "tc")) {
              cells.add(cellText(cell));
              Element props = first(cell, W, "tcPr");
              Element span = props == null ? null : first(props, W, "gridSpan");
              int extra = 0;
              if (span != null) {
                try {
                  extra = Integer.parseInt(attribute(span, W, "val")) - 1;
                } catch (NumberFormatException ignored) {
                  extra = 0;
                }
              }
              for (int i = 0; i < Math.min(extra, 20); i++) {
                cells.add("");
              }
            }
          }
          width = Math.max(width, cells.size());
          rows.add(cells);
        }
      }
      if (rows.isEmpty() || width == 0) {
        return;
      }
      blank();
      for (int r = 0; r < rows.size() && !cut; r++) {
        List<String> cells = rows.get(r);
        StringBuilder row = new StringBuilder("|");
        for (int c = 0; c < width; c++) {
          row.append(' ').append(c < cells.size() ? cells.get(c) : "").append(" |");
        }
        line(row.toString());
        if (r == 0) {
          line("|" + " --- |".repeat(width));
        }
      }
      blank();
      emitPictures();
    }

    private String cellText(Element cell) {
      StringBuilder text = new StringBuilder();
      for (Node node = cell.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element child) {
          String part = "";
          if (is(child, W, "p")) {
            part = inline(child, true).strip();
          } else if (is(child, W, "tbl")) {
            part = flatten(child);
          }
          if (!part.isEmpty()) {
            text.append(text.length() > 0 ? " / " : "").append(part);
          }
        }
      }
      return text.toString().replace("|", "\\|").replace("\n", " ");
    }

    /** A table inside a table cell, as one line of text. */
    private String flatten(Element table) {
      StringBuilder text = new StringBuilder();
      for (Element p : all(table, W, "p")) {
        String line = inline(p, true).strip();
        if (!line.isEmpty()) {
          text.append(text.length() > 0 ? "; " : "").append(line);
        }
      }
      return text.toString();
    }

    // ---- output with a size limit

    private void line(String text) {
      if (cut) {
        return;
      }
      if (chars + text.length() > MAX_CHARS) {
        cut = true;
        return;
      }
      pending.append(text).append('\n');
      chars += text.length() + 1;
    }

    private void blank() {
      if (pending.length() > 0 && !pending.toString().endsWith("\n\n")) {
        pending.append('\n');
      }
    }

    /** Moves the text written so far into a piece of its own (a picture is about to follow). */
    private void flush() {
      if (pending.toString().isBlank()) {
        pending.setLength(0);
        return;
      }
      segments.add(Segment.text(pending.toString().stripTrailing()));
      pending.setLength(0);
    }

    /** The text so far becomes a piece of its own, then the pictures that belonged to it. */
    private void emitPictures() {
      if (after.isEmpty()) {
        return;
      }
      flush();
      segments.addAll(after);
      after.clear();
    }
  }

  // --- small helpers ---------------------------------------------------------------------------

  private static boolean is(Element element, String space, String name) {
    return name.equals(element.getLocalName()) && space.equals(element.getNamespaceURI());
  }

  private static String attribute(Element element, String space, String name) {
    String value = element.getAttributeNS(space, name);
    return value == null ? "" : value;
  }

  static Element first(Element parent, String space, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element child
          && name.equals(child.getLocalName())
          && (space == null || space.equals(child.getNamespaceURI()))) {
        return child;
      }
    }
    return null;
  }

  static List<Element> all(Element parent, String space, String name) {
    List<Element> found = new ArrayList<>();
    var list = parent.getElementsByTagNameNS(space == null ? "*" : space, name);
    for (int i = 0; i < list.getLength(); i++) {
      found.add((Element) list.item(i));
    }
    return found;
  }
}
