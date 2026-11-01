package com.campuscompanion.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** How a long explanatory answer is cut to fit what can be stored. */
class AssistantServiceTest {

  @Test
  void anAnswerThatFitsIsKeptAsItIs() {
    String text = "## Title\n\nA short explanation.";
    assertThat(AssistantService.cutExplanation(text)).isEqualTo(text);
  }

  @Test
  void aLongAnswerIsCutAtABlankLineWithinTheLimit() {
    String paragraph = "word ".repeat(100).strip();
    String text = (paragraph + "\n\n").repeat(30);

    String cut = AssistantService.cutExplanation(text);

    assertThat(cut.length()).isLessThanOrEqualTo(AssistantService.EXPLAIN_REPLY_MAX);
    assertThat(cut).endsWith("word");
  }

  @Test
  void aBlockLeftOpenByTheCutIsClosed() {
    String text = "intro\n\n```flowchart\nflowchart TD\n" + "A --> B\n".repeat(2000);

    String cut = AssistantService.cutExplanation(text);

    assertThat(cut.length()).isLessThanOrEqualTo(AssistantService.EXPLAIN_REPLY_MAX + 4);
    assertThat(cut).endsWith("\n```");
    assertThat(cut.split("```", -1).length - 1).isEven();
  }
}
