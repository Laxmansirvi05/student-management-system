package com.campuscompanion.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Writes the sample attachments of {@link TestDocuments} to a folder, for trying the Quick
 * Assistant by hand with files whose content is known. It does nothing unless a folder is given:
 * {@code ./mvnw test -Dtest=SampleFilesTest -Dsample.dir=/some/folder}.
 */
class SampleFilesTest {

  @Test
  void writeTheSampleFiles() throws Exception {
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
                    "peek() shows the top without removing it.",
                    "Uses: undo, bracket matching, recursion.",
                    "All three operations take O(1) time."))));

    Files.write(
        dir.resolve("handwritten-notes.jpg"),
        TestDocuments.jpeg(
            TestDocuments.handwrittenPage(
                List.of(
                    "Queue notes",
                    "A queue is FIFO - first in first out.",
                    "enqueue adds at the rear.",
                    "dequeue removes from the front.",
                    "Used in CPU scheduling and printers."))));

    Files.write(
        dir.resolve("question-paper.pdf"),
        TestDocuments.textPdf(
            List.of(
                "DATA STRUCTURES - INTERNAL TEST 1 (Page 1 of 3)\n"
                    + "Q1. Define a stack. State two applications of a stack. (4 marks)\n"
                    + "Q2. Convert the binary number 1011 to decimal. (2 marks)\n"
                    + "Q3. What is the time complexity of binary search? (2 marks)",
                "DATA STRUCTURES - INTERNAL TEST 1 (Page 2 of 3)\n"
                    + "Q4. Find the sum of the first 10 natural numbers. (2 marks)\n"
                    + "Q5. Convert the infix expression A+B*C to postfix. (4 marks)\n"
                    + "Q6. Differentiate between a stack and a queue. (5 marks)",
                "DATA STRUCTURES - INTERNAL TEST 1 (Page 3 of 3)\n"
                    + "Q7. A queue holds 3, 8, 5. After dequeue, then enqueue 9, list the queue. (3 marks)\n"
                    + "Q8. What does LIFO stand for? (1 mark)")));

    Files.write(
        dir.resolve("scanned-paper.pdf"),
        TestDocuments.scannedPdf(
            List.of(
                TestDocuments.printedPage(
                    List.of(
                        "SCANNED QUESTION PAPER - PAGE 1",
                        "Q1. What is 12 x 12?",
                        "Q2. Name the data structure that follows FIFO.")),
                TestDocuments.printedPage(
                    List.of(
                        "SCANNED QUESTION PAPER - PAGE 2",
                        "Q3. Convert 1010 from binary to decimal.",
                        "Q4. What is the capital of France?")))));

    Files.write(
        dir.resolve("paper.docx"),
        TestDocuments.questionPaperDocx(TestDocuments.png(TestDocuments.flowchart())));

    Files.write(
        dir.resolve("diagram.pdf"),
        TestDocuments.pdfWithDiagram(
            "Figure 1 shows the working of a queue. Study the figure carefully and answer the question below.\n"
                + "Q. In the figure, which step comes immediately after Enqueue item?",
            TestDocuments.flowchart()));

    Files.write(dir.resolve("flowchart.png"), TestDocuments.png(TestDocuments.flowchart()));
  }
}
