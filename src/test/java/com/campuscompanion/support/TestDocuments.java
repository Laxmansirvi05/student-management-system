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
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Pictures for the attachment tests, drawn in memory with known content: photographs of printed and
 * of handwritten notes, and a flowchart. Nothing here is a real student's document.
 */
public final class TestDocuments {

  private TestDocuments() {}

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
}
