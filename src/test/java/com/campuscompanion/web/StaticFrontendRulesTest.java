package com.campuscompanion.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Task T1-09: the frontend rules that keep the Content Security Policy strict and stop script
 * injection (docs/04-TRD.md TR-FE-4 and TR-FE-5, SECURITY_RULES.md SR-24 and SR-26). Checked on
 * every file under static/, so a new page cannot break them unnoticed.
 */
class StaticFrontendRulesTest {

  private static final Path STATIC = Path.of("src/main/resources/static");

  @Test
  void pagesHaveNoInlineScriptsStylesOrEventHandlers() throws IOException {
    Pattern inlineScript =
        Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
    Pattern inlineStyle = Pattern.compile("<style\\b|\\sstyle\\s*=", Pattern.CASE_INSENSITIVE);
    Pattern eventHandler = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    for (Path page : filesEndingWith(".html")) {
      String html = Files.readString(page);
      assertThat(inlineScript.matcher(html).find()).as("inline script in %s", page).isFalse();
      assertThat(inlineStyle.matcher(html).find()).as("inline style in %s", page).isFalse();
      assertThat(eventHandler.matcher(html).find())
          .as("inline event handler in %s", page)
          .isFalse();
    }
  }

  @Test
  void scriptsNeverInsertTextAsHtml() throws IOException {
    Pattern unsafe =
        Pattern.compile(
            "innerHTML|outerHTML|insertAdjacentHTML|document\\.write|\\beval\\(|new Function\\(");

    for (Path script : filesEndingWith(".js")) {
      assertThat(unsafe.matcher(Files.readString(script)).find())
          .as("unsafe HTML insertion in %s", script)
          .isFalse();
    }
  }

  @Test
  void nothingIsLoadedFromAnotherSite() throws IOException {
    Pattern external =
        Pattern.compile(
            "(src|href)\\s*=\\s*[\"']https?://|url\\(\\s*[\"']?https?://|@import",
            Pattern.CASE_INSENSITIVE);

    for (Path file : filesEndingWith(".html", ".css", ".js")) {
      assertThat(external.matcher(Files.readString(file)).find())
          .as("external resource in %s", file)
          .isFalse();
    }
  }

  @Test
  void everyPageDeclaresItsLanguageAndHasOneMainHeadingOrStatus() throws IOException {
    for (Path page : filesEndingWith(".html")) {
      String html = Files.readString(page);
      assertThat(html).as("%s", page).contains("<html lang=\"en\">").contains("<main");
      assertThat(html.split("<h1", -1).length - 1)
          .as("h1 count in %s", page)
          .isLessThanOrEqualTo(1);
    }
  }

  @Test
  void everyInputHasALabel() throws IOException {
    Pattern input = Pattern.compile("<input[^>]*\\bid=\"([^\"]+)\"");
    for (Path page : filesEndingWith(".html")) {
      String html = Files.readString(page);
      var matcher = input.matcher(html);
      while (matcher.find()) {
        assertThat(html)
            .as("label for %s in %s", matcher.group(1), page)
            .contains("for=\"" + matcher.group(1) + "\"");
      }
    }
  }

  private static List<Path> filesEndingWith(String... suffixes) throws IOException {
    try (Stream<Path> files = Files.walk(STATIC)) {
      List<Path> found =
          files
              .filter(Files::isRegularFile)
              .filter(
                  path -> Stream.of(suffixes).anyMatch(suffix -> path.toString().endsWith(suffix)))
              .toList();
      assertThat(found).as("files ending with %s", List.of(suffixes)).isNotEmpty();
      return found;
    }
  }
}
