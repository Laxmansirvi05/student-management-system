package com.campuscompanion.notes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteTextTest {

  @Test
  void blockBoundariesAreWordBoundaries() {
    assertThat(NoteText.plain("<p>one</p><p>two</p><ul><li>three</li><li>four</li></ul>"))
        .isEqualTo("one two three four");
    assertThat(NoteText.plain("a<br>b&nbsp;c &amp; d &lt;e&gt;")).isEqualTo("a b c & d <e>");
  }

  @Test
  void aSnippetIsCutAtAWordWhenOneIsNear() {
    String text =
        "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau";
    String snippet = NoteText.snippet("<p>" + text + "</p>", 40);
    assertThat(snippet).endsWith("…").hasSizeLessThanOrEqualTo(41);
    assertThat(snippet.substring(0, snippet.length() - 1))
        .isEqualTo(text.substring(0, snippet.length() - 1));
    assertThat(NoteText.snippet("<p>short</p>")).isEqualTo("short");
  }

  @Test
  void anEmptyDocumentHasNoContentButAPictureIs() {
    assertThat(NoteText.hasContent("<p></p>")).isFalse();
    assertThat(NoteText.hasContent("<p>&nbsp;</p>")).isFalse();
    assertThat(NoteText.hasContent("<p>x</p>")).isTrue();
    assertThat(NoteText.hasContent("<p><img src=\"/api/v1/notes/attachments/1/file\"></p>"))
        .isTrue();
    assertThat(NoteText.hasContent(null)).isFalse();
  }

  @Test
  void firstImageAndAttachmentMarkupAreFound() {
    assertThat(NoteText.firstImage("<p>a</p><img src=\"/api/v1/notes/attachments/9/file\">"))
        .isEqualTo("/api/v1/notes/attachments/9/file");
    assertThat(NoteText.firstImage("<p>no image</p>")).isNull();
    assertThat(NoteText.hasAttachmentMarkup("<a data-note-file href=\"/x\">f</a>")).isTrue();
    assertThat(NoteText.hasAttachmentMarkup("<p>plain</p>")).isFalse();
  }
}
