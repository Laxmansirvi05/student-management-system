package com.campuscompanion.coding.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import org.junit.jupiter.api.Test;

/**
 * Each site's answer is turned into numbers and owner text without a network call. The samples are
 * small made-up answers in the shape each site sends.
 */
class PlatformReadersParseTest {

  @Test
  void gitHubGivesReposStarsFollowersAndTheBio() throws Exception {
    PlatformProfile profile =
        GitHubReader.parse(
            """
            {"login":"demo","name":"Demo Student","bio":"Learning. CC-ABCD2345","company":null,
             "public_repos":7,"followers":12}
            """,
            "[{\"stargazers_count\":3},{\"stargazers_count\":4},{\"stargazers_count\":null}]");

    assertThat(profile.stats().publicRepos()).isEqualTo(7);
    assertThat(profile.stats().stars()).isEqualTo(7);
    assertThat(profile.stats().followers()).isEqualTo(12);
    assertThat(profile.stats().problemsSolved()).isNull();
    assertThat(profile.mentions("cc-abcd2345")).isTrue();
    assertThat(profile.mentions("CC-ZZZZ9999")).isFalse();
  }

  @Test
  void leetCodeGivesTheCountsByDifficultyAndTheContestRating() throws Exception {
    PlatformProfile profile =
        LeetCodeReader.parse(
            """
            {"data":{"matchedUser":{"profile":{"aboutMe":"CC-ABCD2345","realName":"Demo"},
              "submitStatsGlobal":{"acSubmissionNum":[
                {"difficulty":"All","count":120},{"difficulty":"Easy","count":70},
                {"difficulty":"Medium","count":40},{"difficulty":"Hard","count":10}]}},
             "userContestRanking":{"rating":1612.7,"attendedContestsCount":9}}}
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(120);
    assertThat(profile.stats().easySolved()).isEqualTo(70);
    assertThat(profile.stats().mediumSolved()).isEqualTo(40);
    assertThat(profile.stats().hardSolved()).isEqualTo(10);
    assertThat(profile.stats().contestRating()).isEqualTo(1613);
    assertThat(profile.stats().contestsAttended()).isEqualTo(9);
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void leetCodeWithNoUserIsNotFound() {
    assertThatThrownBy(() -> LeetCodeReader.parse("{\"data\":{\"matchedUser\":null}}"))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
  }

  @Test
  void codeforcesCountsEachAcceptedProblemOnce() throws Exception {
    PlatformProfile profile =
        CodeforcesReader.parse(
            """
            {"status":"OK","result":[{"handle":"demo","firstName":"CC-ABCD2345","rating":1450,
              "maxRating":1520,"rank":"specialist"}]}
            """,
            """
            {"status":"OK","result":[
              {"verdict":"OK","problem":{"contestId":1,"index":"A"}},
              {"verdict":"OK","problem":{"contestId":1,"index":"A"}},
              {"verdict":"WRONG_ANSWER","problem":{"contestId":1,"index":"B"}},
              {"verdict":"OK","problem":{"contestId":2,"index":"C"}}]}
            """,
            "{\"status\":\"OK\",\"result\":[{},{},{}]}");

    assertThat(profile.stats().problemsSolved()).isEqualTo(2);
    assertThat(profile.stats().contestRating()).isEqualTo(1450);
    assertThat(profile.stats().maxRating()).isEqualTo(1520);
    assertThat(profile.stats().contestsAttended()).isEqualTo(3);
    assertThat(profile.stats().rankLabel()).isEqualTo("specialist");
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void codeChefReadsThePublicPage() throws Exception {
    PlatformProfile profile =
        CodeChefReader.parse(
            """
            <div class="user-details-container"><h1>Demo</h1><span>CC-ABCD2345</span></div>
            <div class="rating-number">1734</div><span>3★</span>
            <small>(Highest Rating 1801)</small>
            <h3>Total Problems Solved: 85</h3>
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(85);
    assertThat(profile.stats().contestRating()).isEqualTo(1734);
    assertThat(profile.stats().maxRating()).isEqualTo(1801);
    assertThat(profile.stats().rankLabel()).isEqualTo("3★");
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void codeChefWithoutAProfileBlockIsNotFound() {
    assertThatThrownBy(() -> CodeChefReader.parse("<html>home page</html>"))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
  }

  @Test
  void geeksforGeeksGivesTheCountAndScore() throws Exception {
    PlatformProfile profile =
        GeeksforGeeksReader.parse(
            """
            {"message":"data retrieved successfully","data":{"name":"Demo","designation":"CC-ABCD2345",
             "institute_name":"A College","total_problems_solved":64,"score":210}}
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(64);
    assertThat(profile.stats().score()).isEqualTo(210);
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void hackerRankAddsTheBadgesAndShowsTheProblemSolvingStars() throws Exception {
    PlatformProfile profile =
        HackerRankReader.parse(
            "{\"model\":{\"username\":\"demo\",\"short_bio\":\"CC-ABCD2345\",\"name\":\"Demo\"}}",
            """
            {"models":[{"badge_type":"problem-solving","solved":30,"stars":4},
                       {"badge_type":"java","solved":12,"stars":2}]}
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(42);
    assertThat(profile.stats().rankLabel()).isEqualTo("4★ Problem Solving");
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void anAnswerThatIsNotJsonMeansTheSiteIsUnavailable() {
    assertThatThrownBy(() -> GeeksforGeeksReader.parse("<html>busy</html>"))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
  }

  @Test
  void httpStatusesMapToNotFoundOrUnavailable() throws Exception {
    assertThat(PlatformHttp.bodyOrFailure(200, "ok")).isEqualTo("ok");
    for (int status : new int[] {404, 302}) {
      assertThatThrownBy(() -> PlatformHttp.bodyOrFailure(status, ""))
          .isInstanceOfSatisfying(
              PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
    }
    assertThatThrownBy(() -> PlatformHttp.bodyOrFailure(400, "{\"message\":\"User not found!\"}"))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
    for (int status : new int[] {400, 429, 500, 503}) {
      assertThatThrownBy(() -> PlatformHttp.bodyOrFailure(status, "busy"))
          .isInstanceOfSatisfying(
              PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }
  }
}
