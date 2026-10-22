package com.campuscompanion.notes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads plain facts out of the HTML of a note, for previews and for deciding whether a note is
 * empty. Display helpers only: the HTML has already been through {@link NoteHtmlSanitizer}.
 */
public final class NoteText {

  static final int SNIPPET_MAX = 160;

  private static final Pattern BLOCK_END =
      Pattern.compile("</(p|div|li|h[1-6]|tr|blockquote|pre)>", Pattern.CASE_INSENSITIVE);
  private static final Pattern BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern IMAGE =
      Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private NoteText() {}

  /** The text of the document with the tags removed and the spaces tidied. */
  public static String plain(String html) {
    if (html == null) {
      return "";
    }
    String text = BLOCK_END.matcher(html).replaceAll(" ");
    text = BREAK.matcher(text).replaceAll(" ");
    text = TAG.matcher(text).replaceAll("");
    return text.replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replaceAll("\\s+", " ")
        .strip();
  }

  /** A short plain-text preview, cut at a word when one is near the end. */
  public static String snippet(String html) {
    return snippet(html, SNIPPET_MAX);
  }

  public static String snippet(String html, int max) {
    String text = plain(html);
    if (text.length() <= max) {
      return text;
    }
    String cut = text.substring(0, max);
    int lastSpace = cut.lastIndexOf(' ');
    return (lastSpace > max * 0.6 ? cut.substring(0, lastSpace) : cut) + "…";
  }

  /** The address of the first picture in the document, or null. */
  public static String firstImage(String html) {
    if (html == null) {
      return null;
    }
    Matcher match = IMAGE.matcher(html);
    return match.find() ? match.group(1) : null;
  }

  /** True when the document has something a reader would see: some text or a picture. */
  public static boolean hasContent(String html) {
    return !plain(html).isEmpty() || (html != null && html.toLowerCase().contains("<img"));
  }

  /** True when the document holds an image or a file chip. */
  public static boolean hasAttachmentMarkup(String html) {
    if (html == null) {
      return false;
    }
    String lower = html.toLowerCase();
    return lower.contains("<img") || lower.contains("data-note-file");
  }
}
