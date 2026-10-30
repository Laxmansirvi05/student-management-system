package com.campuscompanion.coding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BadgePicturesTest {

  @Test
  void onlyPlainPicturePathsOnTheSitesImageHostsAreFetched() {
    assertThat(
            BadgePictures.allowedSource("https://leetcode.com/static/images/badges/guardian.png"))
        .isNotNull();
    assertThat(
            BadgePictures.allowedSource(
                "https://assets.leetcode.com/static_assets/marketing/lg500.png"))
        .isNotNull();
    assertThat(
            BadgePictures.allowedSource("https://cdn.codechef.com/images/badges/problem/gold.svg"))
        .isNotNull();

    assertThat(BadgePictures.allowedSource(null)).isNull();
    assertThat(BadgePictures.allowedSource("http://leetcode.com/a.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://evil.example/a.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://leetcode.com.evil.example/a.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://user@leetcode.com/a.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://leetcode.com:8443/a.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://leetcode.com/a.png?x=1")).isNull();
    assertThat(BadgePictures.allowedSource("https://leetcode.com/static/../admin.png")).isNull();
    assertThat(BadgePictures.allowedSource("https://leetcode.com/static/page.html")).isNull();
  }

  @Test
  void aPictureIsKeptOnlyWhenItsBytesAreAnAllowedImage() {
    assertThat(BadgePictures.typeOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1}))
        .isEqualTo("image/png");
    assertThat(BadgePictures.typeOf("GIF89a...".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo("image/gif");
    assertThat(BadgePictures.typeOf(svg("<path fill=\"url(#paint0)\" d=\"M0 0\"/>")))
        .isEqualTo("image/svg+xml");
    assertThat(BadgePictures.typeOf("<html>not a picture</html>".getBytes(StandardCharsets.UTF_8)))
        .isNull();
    assertThat(BadgePictures.typeOf(new byte[0])).isNull();
  }

  @Test
  void anSvgThatCouldRunOrLoadAnythingIsRefused() {
    assertThat(BadgePictures.typeOf(svg("<script>alert(1)</script>"))).isNull();
    assertThat(BadgePictures.typeOf(svg("<rect onload=\"alert(1)\"/>"))).isNull();
    assertThat(BadgePictures.typeOf(svg("<foreignObject><div/></foreignObject>"))).isNull();
    assertThat(BadgePictures.typeOf(svg("<image href=\"https://evil.example/x.png\"/>"))).isNull();
    assertThat(BadgePictures.typeOf(svg("<a href=\"javascript:alert(1)\"><rect/></a>"))).isNull();
    assertThat(BadgePictures.typeOf(svg("<style>@import 'https://evil.example/x.css';</style>")))
        .isNull();
    assertThat(BadgePictures.typeOf(svg("<rect style=\"fill:url(https://evil.example/x)\"/>")))
        .isNull();
    // A picture embedded as data, or a reference inside the picture, is fine.
    assertThat(
            BadgePictures.typeOf(
                svg("<image href=\"data:image/png;base64,AAAA\"/><use href=\"#a\"/>")))
        .isEqualTo("image/svg+xml");
  }

  @Test
  void theKeyIsTheShaOfTheAddress() {
    assertThat(BadgePictures.keyOf("https://cdn.codechef.com/images/badges/problem/gold.svg"))
        .matches("[0-9a-f]{64}");
  }

  private static byte[] svg(String body) {
    return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 80 80\">" + body + "</svg>")
        .getBytes(StandardCharsets.UTF_8);
  }
}
