package com.campuscompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.notes.NoteImageProcessor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The picture of a Quick Assistant question: what is accepted, what is refused, what is sent. */
class AssistantImageCheckerTest {

  private final AssistantImageChecker checker = new AssistantImageChecker(new NoteImageProcessor());

  private static byte[] picture(String format, int width, int height) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
    return out.toByteArray();
  }

  @Test
  void aJpegAndAPngAreRedrawnAsJpeg() throws Exception {
    for (String[] kind : new String[][] {{"jpeg", "image/jpeg"}, {"png", "image/png"}}) {
      AiImage sent = checker.prepare(picture(kind[0], 80, 40), kind[1]);
      assertThat(sent.mimeType()).isEqualTo("image/jpeg");
      assertThat(sent.data()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }
  }

  @Test
  void aLargePictureIsScaledDownToAtMost1568PixelsOnItsLongSide() throws Exception {
    AiImage sent = checker.prepare(picture("png", 3200, 1800), "image/png");

    BufferedImage seen = ImageIO.read(new ByteArrayInputStream(sent.data()));
    assertThat(Math.max(seen.getWidth(), seen.getHeight())).isLessThanOrEqualTo(1568);
    assertThat(seen.getWidth()).isGreaterThan(seen.getHeight());
  }

  @Test
  void aSmallPictureIsNotEnlarged() throws Exception {
    AiImage sent = checker.prepare(picture("png", 120, 60), "image/png");

    BufferedImage seen = ImageIO.read(new ByteArrayInputStream(sent.data()));
    assertThat(seen.getWidth()).isEqualTo(120);
    assertThat(seen.getHeight()).isEqualTo(60);
  }

  @Test
  void whatIsNotAJpegOrPngIsRefused() throws Exception {
    assertRefused(null, "image/png", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(new byte[0], "image/png", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(picture("gif", 10, 10), "image/gif", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused("<svg/>".getBytes(), "image/svg+xml", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused("text".getBytes(), null, ErrorCode.UPLOAD_INVALID_TYPE);
    // The first bytes must agree with the declared type.
    assertRefused(picture("jpeg", 10, 10), "image/png", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(picture("png", 10, 10), "image/jpeg", ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused("not a picture".getBytes(), "image/jpeg", ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void aPictureThatCannotBeDecodedIsRefused() {
    byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
    assertRefused(truncated, "image/jpeg", ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void aPictureOverFiveMegabytesIsRefusedBeforeItIsDecoded() {
    assertRefused(
        new byte[(int) AssistantImageChecker.MAX_BYTES + 1],
        "image/png",
        ErrorCode.UPLOAD_TOO_LARGE);
  }

  private void assertRefused(byte[] content, String type, ErrorCode code) {
    assertThatThrownBy(() -> checker.prepare(content, type))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
