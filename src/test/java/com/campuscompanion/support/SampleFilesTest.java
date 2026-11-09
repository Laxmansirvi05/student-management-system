package com.campuscompanion.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Writes sample pictures of {@link TestDocuments} to a folder, for trying the Quick Assistant by
 * hand with pictures whose content is known. It does nothing unless a folder is given: {@code
 * ./mvnw test -Dtest=SampleFilesTest -Dsample.dir=/some/folder}.
 */
class SampleFilesTest {

  @Test
  void writeTheSamplePictures() throws Exception {
    String folder = System.getProperty("sample.dir");
    assumeTrue(folder != null && !folder.isBlank(), "no sample.dir given");
    Path dir = Path.of(folder);
    Files.createDirectories(dir);
    Files.write(
        dir.resolve("printed-notes.jpg"),
        TestDocuments.jpeg(
            TestDocuments.printedPage(
                List.of(
                    "Data Structures - Notes on Stacks",
                    "A stack is a LIFO structure: last in, first out.",
                    "push(x) adds x on the top.",
                    "pop() removes the top item.",
                    "All three operations take O(1) time."))));
    Files.write(
        dir.resolve("handwritten-notes.jpg"),
        TestDocuments.jpeg(
            TestDocuments.handwrittenPage(
                List.of(
                    "Queue notes",
                    "A queue is FIFO - first in first out.",
                    "enqueue adds at the rear.",
                    "dequeue removes from the front."))));
    Files.write(dir.resolve("flowchart.png"), TestDocuments.png(TestDocuments.flowchart()));
  }
}
