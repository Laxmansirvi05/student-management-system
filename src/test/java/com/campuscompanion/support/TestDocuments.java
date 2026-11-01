package com.campuscompanion.support;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Documents for the attachment tests, built in memory with known content: a Word document with
 * headings, numbered questions, a table and an equation; PDFs with a text layer, scanned PDFs
 * (pictures of pages with no text), a PDF with a diagram; and photographs of printed and of
 * handwritten notes. Nothing here is a real student's document.
 */
public final class TestDocuments {

  private TestDocuments() {}

  // --- Word ------------------------------------------------------------------------------------

  private static final String NS =
      "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
          + " xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""
          + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
          + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
          + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"";

  public static String paragraph(String text) {
    return "<w:p><w:r><w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
  }

  public static String heading(int level, String text) {
    return "<w:p><w:pPr><w:pStyle w:val=\"Heading"
        + level
        + "\"/></w:pPr><w:r><w:t>"
        + escape(text)
        + "</w:t></w:r></w:p>";
  }

  /** An item of a numbered list (numbering id 1, see {@link #numberingXml()}) at a level. */
  public static String numbered(int level, String text) {
    return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\""
        + level
        + "\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr><w:r><w:t>"
        + escape(text)
        + "</w:t></w:r></w:p>";
  }

  public static String bullet(String text) {
    return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"2\"/></w:numPr></w:pPr>"
        + "<w:r><w:t>"
        + escape(text)
        + "</w:t></w:r></w:p>";
  }

  public static String table(List<List<String>> rows) {
    StringBuilder xml = new StringBuilder("<w:tbl>");
    for (List<String> row : rows) {
      xml.append("<w:tr>");
      for (String cell : row) {
        xml.append("<w:tc>").append(paragraph(cell)).append("</w:tc>");
      }
      xml.append("</w:tr>");
    }
    return xml.append("</w:tbl>").toString();
  }

  /** A paragraph holding the equation (a)/(b) + c^2, in Office Math. */
  public static String equation() {
    return "<w:p><w:r><w:t xml:space=\"preserve\">Solve </w:t></w:r><m:oMath>"
        + "<m:f><m:num><m:r><m:t>a</m:t></m:r></m:num><m:den><m:r><m:t>b</m:t></m:r></m:den></m:f>"
        + "<m:r><m:t>+</m:t></m:r>"
        + "<m:sSup><m:e><m:r><m:t>c</m:t></m:r></m:e><m:sup><m:r><m:t>2</m:t></m:r></m:sup></m:sSup>"
        + "</m:oMath></w:p>";
  }

  /** A paragraph that holds a picture (relationship rId10, the media file added by the caller). */
  public static String pictureParagraph(String description) {
    return "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"1\" name=\"Figure\" descr=\""
        + escape(description)
        + "\"/><a:graphic><a:graphicData><a:pic><a:blipFill><a:blip r:embed=\"rId10\"/></a:blipFill>"
        + "</a:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
  }

  /** The numbering rules: list 1 is "1." and "(a)" at its second level; list 2 is bullets. */
  public static String numberingXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:numbering "
        + NS
        + ">"
        + "<w:abstractNum w:abstractNumId=\"0\">"
        + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/></w:lvl>"
        + "<w:lvl w:ilvl=\"1\"><w:start w:val=\"1\"/><w:numFmt w:val=\"lowerLetter\"/><w:lvlText w:val=\"(%2)\"/></w:lvl>"
        + "</w:abstractNum>"
        + "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/></w:lvl></w:abstractNum>"
        + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
        + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num>"
        + "</w:numbering>";
  }

  /** A .docx whose body is the given XML, with numbering and, when given, one picture (image1). */
  public static byte[] docx(String bodyXml, byte[] picture) {
    Map<String, byte[]> parts = new LinkedHashMap<>();
    parts.put(
        "[Content_Types].xml",
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>")
            .getBytes(StandardCharsets.UTF_8));
    parts.put(
        "_rels/.rels",
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>")
            .getBytes(StandardCharsets.UTF_8));
    parts.put(
        "word/document.xml",
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document "
                + NS
                + "><w:body>"
                + bodyXml
                + "</w:body></w:document>")
            .getBytes(StandardCharsets.UTF_8));
    parts.put("word/numbering.xml", numberingXml().getBytes(StandardCharsets.UTF_8));
    String relations =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + (picture == null
                ? ""
                : "<Relationship Id=\"rId10\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>")
            + "</Relationships>";
    parts.put("word/_rels/document.xml.rels", relations.getBytes(StandardCharsets.UTF_8));
    if (picture != null) {
      parts.put("word/media/image1.png", picture);
    }
    return zip(parts);
  }

  /** A question paper as a Word document: title, instructions, numbered questions, a table. */
  public static byte[] questionPaperDocx(byte[] diagram) {
    StringBuilder body = new StringBuilder();
    body.append(heading(1, "Data Structures Internal Test 2"));
    body.append(paragraph("Time: 1 hour. Answer all questions."));
    body.append(heading(2, "Section A"));
    body.append(numbered(0, "Define a stack and state two applications."));
    body.append(numbered(0, "Convert the infix expression A+B*C to postfix."));
    body.append(numbered(1, "Show each step of the conversion."));
    body.append(numbered(0, "Study the table of marks and answer."));
    body.append(
        table(
            List.of(
                List.of("Question", "Marks"),
                List.of("1", "5"),
                List.of("2", "10"),
                List.of("3", "10"))));
    body.append(equation());
    if (diagram != null) {
      body.append(heading(2, "Section B"));
      body.append(pictureParagraph("Flowchart of a queue with enqueue and dequeue"));
    }
    return docx(body.toString(), diagram);
  }

  // --- PDF -------------------------------------------------------------------------------------

  /** A PDF with a real text layer, one entry of the list per page, wrapped to fit the page. */
  public static byte[] textPdf(List<String> pages) {
    try (PDDocument document = new PDDocument()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (String text : pages) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream out = new PDPageContentStream(document, page)) {
          out.beginText();
          out.setFont(font, 12);
          out.setLeading(16);
          out.newLineAtOffset(56, 780);
          for (String line : text.split("\n")) {
            out.showText(line);
            out.newLine();
          }
          out.endText();
        }
      }
      return bytes(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A PDF with no text at all: every page is a picture, as from a scanner. */
  public static byte[] scannedPdf(List<BufferedImage> scans) {
    try (PDDocument document = new PDDocument()) {
      for (BufferedImage scan : scans) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDImageXObject image = LosslessFactory.createFromImage(document, scan);
        try (PDPageContentStream out = new PDPageContentStream(document, page)) {
          out.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        }
      }
      return bytes(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A PDF page of text with a large diagram drawn under it. */
  public static byte[] pdfWithDiagram(String text, BufferedImage diagram) {
    try (PDDocument document = new PDDocument()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDImageXObject image = LosslessFactory.createFromImage(document, diagram);
      try (PDPageContentStream out = new PDPageContentStream(document, page)) {
        out.beginText();
        out.setFont(font, 12);
        out.setLeading(16);
        out.newLineAtOffset(56, 780);
        for (String line : text.split("\n")) {
          out.showText(line);
          out.newLine();
        }
        out.endText();
        out.drawImage(image, 56, 300, 480, 300);
      }
      return bytes(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // --- pictures --------------------------------------------------------------------------------

  /** A scan or photo of a printed page: dark serif lines on slightly grey paper. */
  public static BufferedImage printedPage(List<String> lines) {
    return page(lines, new Font(Font.SERIF, Font.PLAIN, 34), new Color(25, 25, 30), 0.0, 99);
  }

  /** A photo of handwriting: a handwriting font in blue ink, each line a little off the level. */
  public static BufferedImage handwrittenPage(List<String> lines) {
    Font hand = new Font("Bradley Hand", Font.BOLD, 40);
    if (!"Bradley Hand".equalsIgnoreCase(hand.getFamily())) {
      hand = new Font("Chalkboard SE", Font.PLAIN, 40);
    }
    return page(lines, hand, new Color(20, 40, 140), 0.012, 7);
  }

  private static BufferedImage page(
      List<String> lines, Font font, Color ink, double skew, long seed) {
    int width = 1240;
    int height = 1754;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(new Color(244, 242, 236));
    g.fillRect(0, 0, width, height);
    Random random = new Random(seed);
    for (int i = 0; i < 4000; i++) {
      int shade = 225 + random.nextInt(25);
      g.setColor(new Color(shade, shade, shade - 4));
      g.fillRect(random.nextInt(width), random.nextInt(height), 2, 2);
    }
    g.setFont(font);
    g.setColor(ink);
    int y = 150;
    for (String line : lines) {
      AffineTransform keep = g.getTransform();
      g.rotate(skew * (random.nextDouble() * 2 - 0.6), 110, y);
      g.drawString(line, 110, y + random.nextInt(6));
      g.setTransform(keep);
      y += 78;
    }
    g.dispose();
    return image;
  }

  /** A box-and-arrow diagram: Start, "Enqueue item", "Queue full?", "Dequeue item", End. */
  public static BufferedImage flowchart() {
    BufferedImage image = new BufferedImage(900, 560, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 900, 560);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
    String[] labels = {"Start", "Enqueue item", "Queue full?", "Dequeue item", "End"};
    int x = 330;
    for (int i = 0; i < labels.length; i++) {
      int y = 20 + i * 105;
      g.setColor(Color.BLACK);
      g.setStroke(new java.awt.BasicStroke(3));
      if (i == 2) {
        g.drawPolygon(
            new int[] {x + 120, x + 240, x + 120, x}, new int[] {y, y + 40, y + 80, y + 40}, 4);
      } else {
        g.drawRoundRect(x, y, 240, 70, 24, 24);
      }
      g.drawString(labels[i], x + 120 - g.getFontMetrics().stringWidth(labels[i]) / 2, y + 44);
      if (i < labels.length - 1) {
        g.drawLine(x + 120, y + (i == 2 ? 80 : 70), x + 120, y + 105);
      }
    }
    g.dispose();
    return image;
  }

  public static byte[] jpeg(BufferedImage image) {
    return write(image, "jpg");
  }

  public static byte[] png(BufferedImage image) {
    return write(image, "png");
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static byte[] write(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] bytes(PDDocument document) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }

  private static byte[] zip(Map<String, byte[]> parts) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
        for (Map.Entry<String, byte[]> part : parts.entrySet()) {
          zip.putNextEntry(new ZipEntry(part.getKey()));
          zip.write(part.getValue());
          zip.closeEntry();
        }
      }
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
