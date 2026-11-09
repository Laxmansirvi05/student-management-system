package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the model is given for a question with pictures: the list and the pictures, in order. */
class DocumentContextTest {

  private static ReadFile picture(String name, int n) {
    return new ReadFile(
        name, ReadFile.Kind.IMAGE, List.of(new AiImage("image/jpeg", new byte[] {(byte) n})));
  }

  @Test
  void picturesAndTheirNamesComeInTheSameOrder() {
    DocumentContext.Built built =
        DocumentContext.build(List.of(picture("notes.jpg", 1), picture("shot.png", 2)));
    assertThat(built.pictures()).extracting(p -> p.data()[0]).containsExactly((byte) 1, (byte) 2);
    assertThat(built.text()).isEqualTo("Picture 1: \"notes.jpg\"\nPicture 2: \"shot.png\"");
    assertThat(built.shortened()).isFalse();
  }

  @Test
  void noFilesGiveNothing() {
    assertThat(DocumentContext.build(List.of()).isEmpty()).isTrue();
  }

  @Test
  void morePicturesThanTheLimitAreNotSentAndTheTextSaysSo() {
    List<ReadFile> files =
        java.util.stream.IntStream.range(0, 8).mapToObj(i -> picture("p" + i + ".jpg", i)).toList();
    DocumentContext.Built built = DocumentContext.build(files);
    assertThat(built.pictures()).hasSize(DocumentContext.MAX_PICTURES);
    assertThat(built.text())
        .contains("Picture 5:")
        .doesNotContain("Picture 6:")
        .contains("Say that you have not seen the rest");
    assertThat(built.shortened()).isTrue();
  }
}
