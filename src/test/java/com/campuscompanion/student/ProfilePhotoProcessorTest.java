package com.campuscompanion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.support.TestImages;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** What is accepted as a profile photo, and what is stored in its place. */
class ProfilePhotoProcessorTest {

  private static final String JPEG = "image/jpeg";
  private static final String PNG = "image/png";

  private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor();

  @Test
  void anyAcceptedPhotoIsStoredAsASquareJpegOfFixedSize() {
    byte[] fromJpeg = processor.process(TestImages.jpeg(900, 600), JPEG);
    byte[] fromPng = processor.process(TestImages.pngWithTransparency(300, 700), PNG);

    for (byte[] stored : new byte[][] {fromJpeg, fromPng}) {
      assertThat(Arrays.copyOf(stored, 3)).containsExactly(0xFF, 0xD8, 0xFF);
      BufferedImage picture = TestImages.read(stored);
      assertThat(picture.getWidth()).isEqualTo(512);
      assertThat(picture.getHeight()).isEqualTo(512);
    }
  }

  @Test
  void theMiddleOfThePictureIsKept() {
    // 900 by 600, red on the left and blue on the right. The middle 600 by 600 keeps both.
    BufferedImage picture = TestImages.read(processor.process(TestImages.jpeg(900, 600), JPEG));

    assertRed(picture, 60, 256);
    assertBlue(picture, 450, 256);
  }

  @Test
  void transparentPartsBecomeWhite() {
    BufferedImage picture =
        TestImages.read(processor.process(TestImages.pngWithTransparency(400, 400), PNG));

    Color corner = new Color(picture.getRGB(10, 10));
    Color middle = new Color(picture.getRGB(256, 256));
    assertThat(corner.getRed()).isGreaterThan(240);
    assertThat(corner.getGreen()).isGreaterThan(240);
    assertThat(corner.getBlue()).isGreaterThan(240);
    assertThat(middle.getGreen()).isGreaterThan(200);
    assertThat(middle.getRed()).isLessThan(60);
  }

  @Test
  void nothingHiddenInTheUploadReachesTheStoredPicture() {
    byte[] upload = TestImages.withExif(TestImages.jpeg(800, 800), 1, "GPS-12.9716N-77.5946E");
    assertThat(new String(upload, StandardCharsets.ISO_8859_1)).contains("GPS-12.9716N-77.5946E");

    byte[] stored = processor.process(upload, JPEG);

    assertThat(new String(stored, StandardCharsets.ISO_8859_1))
        .doesNotContain("GPS-12.9716N")
        .doesNotContain("Exif");
  }

  @Test
  void aPhotoStoredOnItsSideIsTurnedUpright() {
    // Red on the left, blue on the right. Orientation 6 means "turn a quarter clockwise to view",
    // which brings the left side to the top.
    byte[] sideways = TestImages.withExif(TestImages.jpeg(800, 800), 6, "");

    BufferedImage picture = TestImages.read(processor.process(sideways, JPEG));

    assertRed(picture, 256, 60);
    assertBlue(picture, 256, 450);
  }

  @Test
  void theOrientationIsReadFromBothByteOrdersAndDefaultsToNone() {
    byte[] plain = TestImages.jpeg(100, 100);

    assertThat(ProfilePhotoProcessor.orientationOf(plain)).isEqualTo(1);
    assertThat(ProfilePhotoProcessor.orientationOf(TestImages.withExif(plain, 3, "x")))
        .isEqualTo(3);
    assertThat(ProfilePhotoProcessor.orientationOf(TestImages.withExif(plain, 8, "x")))
        .isEqualTo(8);
    // A value outside 1 to 8 means nothing.
    assertThat(ProfilePhotoProcessor.orientationOf(TestImages.withExif(plain, 42, "x")))
        .isEqualTo(1);
  }

  @Test
  void brokenOrHostileHeadersNeverCauseAnErrorWhenReadingTheOrientation() {
    byte[] withExif = TestImages.withExif(TestImages.jpeg(100, 100), 6, "x");
    Random random = new Random(7);

    // Every shortened copy, and many copies with random bytes changed, are read without failing.
    for (int length = 0; length < 60; length++) {
      byte[] cut = Arrays.copyOf(withExif, length);
      assertThatCode(() -> ProfilePhotoProcessor.orientationOf(cut)).doesNotThrowAnyException();
    }
    for (int round = 0; round < 500; round++) {
      byte[] damaged = withExif.clone();
      for (int change = 0; change < 6; change++) {
        damaged[2 + random.nextInt(40)] = (byte) random.nextInt(256);
      }
      assertThatCode(() -> ProfilePhotoProcessor.orientationOf(damaged)).doesNotThrowAnyException();
      assertThat(ProfilePhotoProcessor.orientationOf(damaged)).isBetween(1, 8);
    }
  }

  @Test
  void whatIsNotAJpegOrPngPictureIsRefused() {
    assertInvalid("plain text".getBytes(StandardCharsets.UTF_8), JPEG);
    assertInvalid(TestImages.gif(200, 200), "image/gif");
    assertInvalid(TestImages.gif(200, 200), PNG);
    assertInvalid(new byte[0], JPEG);
    // The right first bytes, and nothing real after them.
    assertInvalid(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}, JPEG);
    assertInvalid(Arrays.copyOf(TestImages.jpeg(400, 400), 120), JPEG);
  }

  @Test
  void theDeclaredTypeMustMatchTheContent() {
    assertInvalid(TestImages.jpeg(200, 200), PNG);
    assertInvalid(TestImages.pngWithTransparency(200, 200), JPEG);
    assertInvalid(TestImages.jpeg(200, 200), null);
    assertInvalid(TestImages.jpeg(200, 200), "image/svg+xml");
  }

  @Test
  void aPictureThatIsTooSmallOrTooLargeIsRefused() {
    assertInvalid(TestImages.jpeg(63, 200), JPEG);
    assertInvalid(TestImages.jpeg(6001, 64), JPEG);

    // The smallest and the largest sides that are allowed.
    assertThatCode(() -> processor.process(TestImages.jpeg(64, 64), JPEG))
        .doesNotThrowAnyException();
    assertThatCode(() -> processor.process(TestImages.jpeg(6000, 64), JPEG))
        .doesNotThrowAnyException();
  }

  @Test
  void aLargePictureIsReadAtLowerResolutionAndStillComesOutRight() {
    // 4000 by 3000 is read with every third pixel. The middle square still keeps both colours.
    BufferedImage picture = TestImages.read(processor.process(TestImages.jpeg(4000, 3000), JPEG));

    assertThat(picture.getWidth()).isEqualTo(512);
    assertRed(picture, 60, 256);
    assertBlue(picture, 450, 256);
  }

  @Test
  void aFileOverTwoMegabytesIsRefusedBeforeItIsOpened() {
    byte[] huge = new byte[2 * 1024 * 1024 + 1];
    huge[0] = (byte) 0xFF;
    huge[1] = (byte) 0xD8;
    huge[2] = (byte) 0xFF;

    assertThatThrownBy(() -> processor.process(huge, JPEG))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE));
  }

  private void assertInvalid(byte[] content, String declaredType) {
    assertThatThrownBy(() -> processor.process(content, declaredType))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE));
  }

  private static void assertRed(BufferedImage picture, int x, int y) {
    Color colour = new Color(picture.getRGB(x, y));
    assertThat(colour.getRed()).as("red at %d,%d", x, y).isGreaterThan(180);
    assertThat(colour.getBlue()).as("blue at %d,%d", x, y).isLessThan(80);
  }

  private static void assertBlue(BufferedImage picture, int x, int y) {
    Color colour = new Color(picture.getRGB(x, y));
    assertThat(colour.getBlue()).as("blue at %d,%d", x, y).isGreaterThan(180);
    assertThat(colour.getRed()).as("red at %d,%d", x, y).isLessThan(80);
  }
}
