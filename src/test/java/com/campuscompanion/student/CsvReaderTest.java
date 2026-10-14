package com.campuscompanion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.student.CsvReader.MalformedCsvException;
import com.campuscompanion.student.CsvReader.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

  @Test
  void splitsLinesAndCommas() {
    List<Row> rows = CsvReader.read("a,b,c\n1,2,3\n");

    assertThat(rows)
        .containsExactly(new Row(1, List.of("a", "b", "c")), new Row(2, List.of("1", "2", "3")));
  }

  @Test
  void acceptsWindowsAndOldMacLineEndings() {
    assertThat(CsvReader.read("a,b\r\n1,2\r\n")).extracting(Row::fields).hasSize(2);
    assertThat(CsvReader.read("a,b\r1,2")).extracting(Row::fields).hasSize(2);
  }

  @Test
  void aQuotedFieldMayHoldCommasQuotesAndLineBreaks() {
    List<Row> rows =
        CsvReader.read("name,note\n\"Rao, Demo\",\"says \"\"hi\"\"\nand bye\"\nnext,x");

    assertThat(rows.get(1).fields()).containsExactly("Rao, Demo", "says \"hi\"\nand bye");
    // The quoted line break counts, so the row after it is on line 4.
    assertThat(rows.get(2)).isEqualTo(new Row(4, List.of("next", "x")));
  }

  @Test
  void keepsEmptyFieldsButLeavesOutEmptyLines() {
    List<Row> rows = CsvReader.read("a,b,c\n\n1,,3\n , \n,,\n");

    assertThat(rows)
        .containsExactly(new Row(1, List.of("a", "b", "c")), new Row(3, List.of("1", "", "3")));
  }

  @Test
  void ignoresTheByteOrderMarkThatSpreadsheetsAdd() {
    assertThat(CsvReader.read(CsvReader.BYTE_ORDER_MARK + "rollNo,fullName").get(0).fields())
        .containsExactly("rollNo", "fullName");
  }

  @Test
  void aQuoteInsideAnUnquotedFieldIsKeptAsText() {
    assertThat(CsvReader.read("5\" disk,x").get(0).fields()).containsExactly("5\" disk", "x");
  }

  @Test
  void anUnclosedQuoteIsAnErrorWithItsLine() {
    assertThatThrownBy(() -> CsvReader.read("a,b\n1,\"never closed\n2,3"))
        .isInstanceOf(MalformedCsvException.class)
        .hasMessageContaining("line 2")
        .extracting(e -> ((MalformedCsvException) e).getLine())
        .isEqualTo(2);
  }

  @Test
  void emptyTextHasNoRows() {
    assertThat(CsvReader.read("")).isEmpty();
    assertThat(CsvReader.read(CsvReader.BYTE_ORDER_MARK + "\n\n")).isEmpty();
  }
}
