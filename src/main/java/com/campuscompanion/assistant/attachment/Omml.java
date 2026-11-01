package com.campuscompanion.assistant.attachment;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * An equation of a Word document (Office Math, "OMML") as plain text a model can read: a fraction
 * is (top)/(bottom), a power x^(2), an index x_(i), a root √(…), a sum or integral with its limits.
 * It follows the structure of the equation; an element it does not know is written as the text it
 * holds.
 */
final class Omml {

  private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";

  private Omml() {}

  static String text(Element math) {
    return children(math).strip();
  }

  private static String children(Element parent) {
    StringBuilder text = new StringBuilder();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element child) {
        text.append(element(child));
      }
    }
    return text.toString();
  }

  private static String element(Element e) {
    if (!M.equals(e.getNamespaceURI())) {
      return e.getTextContent();
    }
    return switch (e.getLocalName()) {
      case "t" -> e.getTextContent();
      case "f" -> "(" + part(e, "num") + ")/(" + part(e, "den") + ")";
      case "sSup" -> part(e, "e") + "^(" + part(e, "sup") + ")";
      case "sSub" -> part(e, "e") + "_(" + part(e, "sub") + ")";
      case "sSubSup" -> part(e, "e") + "_(" + part(e, "sub") + ")^(" + part(e, "sup") + ")";
      case "rad" -> {
        String degree = part(e, "deg");
        yield (degree.isBlank() ? "√" : "root[" + degree + "]") + "(" + part(e, "e") + ")";
      }
      case "nary" -> {
        String symbol = symbol(e, "naryPr", "chr", "∫");
        String low = part(e, "sub");
        String high = part(e, "sup");
        yield symbol
            + (low.isBlank() ? "" : "_(" + low + ")")
            + (high.isBlank() ? "" : "^(" + high + ")")
            + "("
            + part(e, "e")
            + ")";
      }
      case "d" -> {
        String open = symbol(e, "dPr", "begChr", "(");
        String close = symbol(e, "dPr", "endChr", ")");
        String separator = symbol(e, "dPr", "sepChr", "|");
        StringBuilder items = new StringBuilder();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
          if (n instanceof Element item && "e".equals(item.getLocalName())) {
            items.append(items.length() > 0 ? separator : "").append(children(item));
          }
        }
        yield open + items + close;
      }
      case "func" -> part(e, "fName") + "(" + part(e, "e") + ")";
      case "limLow" -> part(e, "e") + "_(" + part(e, "lim") + ")";
      case "limUpp" -> part(e, "e") + "^(" + part(e, "lim") + ")";
      case "bar" -> "overline(" + part(e, "e") + ")";
      case "acc" -> symbol(e, "accPr", "chr", "^") + "(" + part(e, "e") + ")";
      case "m" -> matrix(e);
      case "rPr", "ctrlPr", "fPr", "dPr", "naryPr", "accPr", "radPr", "sSupPr", "sSubPr", "barPr" ->
          "";
      default -> children(e);
    };
  }

  private static String matrix(Element matrix) {
    StringBuilder text = new StringBuilder("[");
    boolean firstRow = true;
    for (Node row = matrix.getFirstChild(); row != null; row = row.getNextSibling()) {
      if (row instanceof Element r && "mr".equals(r.getLocalName())) {
        text.append(firstRow ? "" : "; ");
        firstRow = false;
        boolean firstCell = true;
        for (Node cell = r.getFirstChild(); cell != null; cell = cell.getNextSibling()) {
          if (cell instanceof Element c && "e".equals(c.getLocalName())) {
            text.append(firstCell ? "" : ", ").append(children(c));
            firstCell = false;
          }
        }
      }
    }
    return text.append("]").toString();
  }

  /** The text inside the child of this name, or nothing. */
  private static String part(Element parent, String name) {
    Element child = DocxReader.first(parent, M, name);
    return child == null ? "" : children(child);
  }

  /** The "val" of a property such as the symbol of a sum, with a default. */
  private static String symbol(Element parent, String properties, String name, String fallback) {
    Element group = DocxReader.first(parent, M, properties);
    Element child = group == null ? null : DocxReader.first(group, M, name);
    if (child == null) {
      return fallback;
    }
    String value = child.getAttributeNS(M, "val");
    return value == null ? fallback : value;
  }
}
