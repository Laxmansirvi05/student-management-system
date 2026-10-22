package com.campuscompanion.notes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The note sanitizer: what the editor can produce survives, and nothing that can run does. */
class NoteHtmlSanitizerTest {

  private static String clean(String html) {
    return NoteHtmlSanitizer.sanitize(html);
  }

  @Test
  void anEmptyOrMissingDocumentIsEmpty() {
    assertThat(clean(null)).isEmpty();
    assertThat(clean("")).isEmpty();
  }

  @Test
  void formattingTheEditorProducesSurvives() {
    String html =
        "<h2 style=\"text-align: center;\">Title</h2><p>Some <strong>bold</strong>, <em>italic</em>,"
            + " <u>underlined</u> and <s>struck</s> text with <code>code</code>.</p>"
            + "<blockquote><p>Quote</p></blockquote><pre class=\"language-java\"><code>int x;</code></pre>";
    assertThat(clean(html)).isEqualTo(html);
  }

  @Test
  void colourAndSizeStylesSurviveButOtherStylesDoNot() {
    String result =
        clean(
            "<p style=\"color: #ff0000; background-color: rgb(1, 2, 3); font-size: 18px;"
                + " position: fixed; background: url(http://evil.example/x.png);"
                + " width: expression(alert(1))\">x</p>");
    assertThat(result)
        .isEqualTo(
            "<p style=\"color: #ff0000; background-color: rgb(1, 2, 3); font-size: 18px;\">x</p>");
  }

  @Test
  void aStyleWithNothingAllowedIsRemoved() {
    assertThat(clean("<p style=\"position:fixed;top:0\">x</p>")).isEqualTo("<p>x</p>");
  }

  @Test
  void scriptsAreRemovedWithTheirContent() {
    assertThat(clean("<p>a</p><script>alert(1)</script><p>b</p>")).isEqualTo("<p>a</p><p>b</p>");
    assertThat(clean("<SCRIPT>alert(1)</SCRIPT>ok")).isEqualTo("ok");
    assertThat(clean("<style>body{display:none}</style>ok")).isEqualTo("ok");
    assertThat(clean("<scr<script>ipt>alert(1)</scr</script>ipt>")).doesNotContain("<script");
  }

  @Test
  void eventHandlersAndUnknownAttributesAreRemoved() {
    assertThat(clean("<p onclick=\"evil()\" id=\"x\" class=\"y\">t</p>")).isEqualTo("<p>t</p>");
    assertThat(clean("<a href=\"https://example.org\" onclick=\"evil()\">l</a>"))
        .isEqualTo(
            "<a href=\"https://example.org\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">l</a>");
  }

  @Test
  void linksMustBeWebMailPhoneOrInternal() {
    assertThat(clean("<a href=\"javascript:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
    assertThat(clean("<a href=\" javascript:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
    assertThat(clean("<a href=\"java&#115;cript:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
    assertThat(clean("<a href=\"data:text/html,<script>alert(1)</script>\">x</a>"))
        .doesNotContain("data:");
    assertThat(clean("<a href=\"//evil.example\">x</a>")).isEqualTo("<a>x</a>");
    assertThat(clean("<a href=\"mailto:a@b.example\">x</a>")).contains("mailto:a@b.example");
    assertThat(clean("<a href=\"tel:+911234567890\">x</a>")).contains("tel:");
    // A path inside the application keeps opening in the same tab.
    assertThat(
            clean(
                "<a href=\"/student/notes.html?note=5\" data-note-link=\"5\" data-link-type=\"note\">n</a>"))
        .isEqualTo(
            "<a href=\"/student/notes.html?note=5\" data-note-link=\"5\" data-link-type=\"note\">n</a>");
  }

  @Test
  void imagesMayOnlyPointAtTheApplicationsOwnAttachments() {
    assertThat(
            clean(
                "<img src=\"/api/v1/notes/attachments/12/file\" alt=\"Diagram\" width=\"320\" data-align=\"center\">"))
        .isEqualTo(
            "<img src=\"/api/v1/notes/attachments/12/file\" alt=\"Diagram\" width=\"320\" data-align=\"center\">");
    assertThat(clean("<img src=\"https://tracker.example/pixel.gif\">")).isEmpty();
    assertThat(clean("<img src=\"data:image/png;base64,AAAA\">")).isEmpty();
    assertThat(clean("<img src=\"javascript:alert(1)\">")).isEmpty();
    assertThat(clean("<img src=x onerror=alert(1)>")).isEmpty();
    assertThat(clean("<img src=\"/api/v1/notes/attachments/12/file\" onerror=\"alert(1)\">"))
        .isEqualTo("<img src=\"/api/v1/notes/attachments/12/file\">");
    assertThat(clean("<img src=\"/api/v1/notes/attachments/12/file/../../x\">")).isEmpty();
  }

  @Test
  void aFileChipSurvives() {
    String chip =
        "<a href=\"/api/v1/notes/attachments/7/file?download=true\" data-file-name=\"Notes.pdf\" data-file-size=\"2048\" data-note-file>Notes.pdf</a>";
    assertThat(clean(chip)).contains("data-note-file").contains("data-file-name=\"Notes.pdf\"");
  }

  @Test
  void checklistsKeepTheirStructure() {
    String html =
        "<ul data-type=\"taskList\"><li data-type=\"taskItem\" data-checked=\"true\"><label>"
            + "<input type=\"checkbox\" checked></label><div><p>Done</p></div></li></ul>";
    assertThat(clean(html)).isEqualTo(html);
    assertThat(clean("<input type=\"text\" value=\"x\">")).isEmpty();
  }

  @Test
  void tablesKeepTheirShape() {
    String html =
        "<table style=\"width: 100%;\"><tbody><tr><th colspan=\"2\" colwidth=\"100,200\">H</th></tr>"
            + "<tr><td>a</td><td>b</td></tr></tbody></table>";
    assertThat(clean(html)).isEqualTo(html);
  }

  @Test
  void dangerousElementsAreRemoved() {
    for (String tag :
        new String[] {
          "iframe", "object", "embed", "form", "svg", "math", "video", "link", "meta", "base"
        }) {
      assertThat(clean("<" + tag + " src=\"https://evil.example\">x</" + tag + ">"))
          .doesNotContain("<" + tag);
    }
  }

  @Test
  void textIsEscapedSoItCannotBecomeMarkup() {
    assertThat(clean("<p>a < b and c > d</p>")).isEqualTo("<p>a &lt; b and c &gt; d</p>");
    assertThat(clean("<p>fish & chips</p>")).isEqualTo("<p>fish &amp; chips</p>");
    assertThat(clean("<p>fish &amp; chips&nbsp;now</p>"))
        .isEqualTo("<p>fish &amp; chips&nbsp;now</p>");
    assertThat(clean("<p>1 <2</p>")).isEqualTo("<p>1 &lt;2</p>");
    assertThat(clean("<p>unfinished <b")).isEqualTo("<p>unfinished </p>");
  }

  @Test
  void commentsAreDropped() {
    assertThat(clean("a<!-- hidden <script>x</script> -->b")).isEqualTo("ab");
    assertThat(clean("a<![CDATA[x]]>b")).doesNotContain("CDATA");
  }

  @Test
  void unbalancedTagsAreClosedAndStrayClosersIgnored() {
    assertThat(clean("<p><strong>bold")).isEqualTo("<p><strong>bold</strong></p>");
    assertThat(clean("text</p></div>more")).isEqualTo("textmore");
    assertThat(clean("<p><em>a</p>b")).isEqualTo("<p><em>a</em></p>b");
  }

  @Test
  void attributeValuesWithAngleBracketsCannotEndTheTagEarly() {
    String result =
        clean(
            "<img alt=\"a > <script>alert(1)</script>\" src=\"/api/v1/notes/attachments/3/file\">");
    assertThat(result).doesNotContain("<script");
    assertThat(result).startsWith("<img");
  }

  @Test
  void extremeNestingIsLimited() {
    String deep = "<div>".repeat(500) + "x" + "</div>".repeat(500);
    String result = clean(deep);
    assertThat(result.chars().filter(c -> c == '<').count()).isLessThan(500);
    assertThat(result).contains("x");
  }

  @Test
  void theResultIsAFixedPoint() {
    String html =
        "<h1>T</h1><p style=\"text-align: right;\">a &amp; b <a href=\"https://example.org/?a=1&amp;b=2\">l</a></p>"
            + "<ul><li><p>x</p></li></ul><img src=\"/api/v1/notes/attachments/1/file\">";
    String once = clean(html);
    assertThat(clean(once)).isEqualTo(once);
  }
}
