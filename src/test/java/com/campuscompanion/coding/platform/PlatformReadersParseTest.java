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
            "demo",
            """
            {"login":"demo","name":"Demo Student","bio":"Learning. CC-ABCD2345","company":null,
             "public_repos":7,"followers":12}
            """,
            """
            [{"name":"site","stargazers_count":3,"language":"HTML","fork":false,"pushed_at":"2026-10-01"},
             {"name":"app","stargazers_count":4,"language":"Java","description":"A\\napp","fork":false},
             {"name":"copy","stargazers_count":null,"language":"Java","fork":true},
             {"name":"../bad","stargazers_count":0,"language":null,"fork":false}]
            """,
            """
            <td data-date="2026-10-01" id="contribution-day-component-0-0" data-level="1"></td>
            <td data-date="2026-10-02" id="contribution-day-component-0-1" data-level="0"></td>
            <tool-tip for="contribution-day-component-0-0" class="sr-only">1,204 contributions on October 1st.</tool-tip>
            <tool-tip for="contribution-day-component-0-1" class="sr-only">No contributions on October 2nd.</tool-tip>
            """,
            743,
            12,
            null);

    assertThat(profile.stats().publicRepos()).isEqualTo(7);
    assertThat(profile.stats().stars()).isEqualTo(7);
    assertThat(profile.stats().followers()).isEqualTo(12);
    assertThat(profile.stats().problemsSolved()).isNull();
    assertThat(profile.mentions("cc-abcd2345")).isTrue();
    assertThat(profile.mentions("CC-ZZZZ9999")).isFalse();
    PlatformDetails details = profile.details();
    assertThat(details.calendar()).containsEntry("2026-10-01", 1204).containsEntry("2026-10-02", 0);
    assertThat(details.languages()).containsEntry("Java", 2).containsEntry("HTML", 1);
    // Forks are not shown, a name that is not a plain repository name is dropped, and the address
    // is built from the handle.
    assertThat(details.projects())
        .extracting(PlatformDetails.Project::name)
        .containsExactly("app", "site");
    assertThat(details.projects().get(0).url()).isEqualTo("https://github.com/demo/app");
    assertThat(details.projects().get(0).description()).isEqualTo("A app");
    assertThat(details.commits()).isEqualTo(743);
    assertThat(details.pullRequests()).isEqualTo(12);
    assertThat(details.issues()).isNull();
  }

  @Test
  void leetCodeGivesTheCountsByDifficultyAndTheContestRating() throws Exception {
    PlatformProfile profile =
        LeetCodeReader.parse(
            """
            {"data":{"matchedUser":{"profile":{"aboutMe":"CC-ABCD2345","realName":"Demo"},
              "submitStatsGlobal":{"acSubmissionNum":[
                {"difficulty":"All","count":120},{"difficulty":"Easy","count":70},
                {"difficulty":"Medium","count":40},{"difficulty":"Hard","count":10}]},
              "userCalendar":{"submissionCalendar":"{\\"1790812800\\": 3, \\"1790899200\\": 2}"},
              "tagProblemCounts":{"fundamental":[{"tagName":"Array","problemsSolved":53}],
                "intermediate":[{"tagName":"Hash Table","problemsSolved":25}],"advanced":[]},
              "badges":[{"displayName":"50 Days Badge 2026","icon":"/static/images/badges/dcc-2026.png"}],
              "languageProblemCount":[{"languageName":"Java","problemsSolved":100}]},
             "userContestRanking":{"rating":1612.7,"attendedContestsCount":9}}}
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(120);
    assertThat(profile.stats().easySolved()).isEqualTo(70);
    assertThat(profile.stats().mediumSolved()).isEqualTo(40);
    assertThat(profile.stats().hardSolved()).isEqualTo(10);
    assertThat(profile.stats().contestRating()).isEqualTo(1613);
    assertThat(profile.stats().contestsAttended()).isEqualTo(9);
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
    assertThat(profile.details().calendar())
        .containsEntry("2026-10-01", 3)
        .containsEntry("2026-10-02", 2);
    assertThat(profile.details().topics())
        .containsEntry("Array", 53)
        .containsEntry("Hash Table", 25);
    assertThat(profile.details().badges())
        .containsExactly(
            new PlatformDetails.Badge(
                "50 Days Badge 2026",
                null,
                "https://leetcode.com/static/images/badges/dcc-2026.png",
                null));
    assertThat(profile.details().languages()).containsEntry("Java", 100);
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
              {"verdict":"OK","creationTimeSeconds":1790812800,"problem":{"contestId":1,"index":"A","tags":["math","greedy"]}},
              {"verdict":"OK","creationTimeSeconds":1790812900,"problem":{"contestId":1,"index":"A","tags":["math","greedy"]}},
              {"verdict":"WRONG_ANSWER","creationTimeSeconds":1790899200,"problem":{"contestId":1,"index":"B","tags":["dp"]}},
              {"verdict":"OK","creationTimeSeconds":1790899300,"problem":{"contestId":2,"index":"C","tags":["math"]}}]}
            """,
            "{\"status\":\"OK\",\"result\":[{},{},{}]}");

    assertThat(profile.stats().problemsSolved()).isEqualTo(2);
    assertThat(profile.stats().contestRating()).isEqualTo(1450);
    assertThat(profile.stats().maxRating()).isEqualTo(1520);
    assertThat(profile.stats().contestsAttended()).isEqualTo(3);
    assertThat(profile.stats().rankLabel()).isEqualTo("specialist");
    // Every submission counts on its day; topics come from accepted problems, once each.
    assertThat(profile.details().calendar())
        .containsEntry("2026-10-01", 2)
        .containsEntry("2026-10-02", 2);
    assertThat(profile.details().topics())
        .containsEntry("Math", 2)
        .containsEntry("Greedy", 1)
        .doesNotContainKey("Dp");
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
            <div class='badge'><img src='https://cdn.codechef.com/images/badges/problem/gold.svg' alt='Problem Solver - Gold Badge'/>
            <div class='badge__title'>Problem Solver - Gold Badge</div></div>
            <script>var userDailySubmissionsStats = [{"date":"2026-10-1","value":4},{"date":"bad","value":1}];</script>
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(85);
    assertThat(profile.stats().contestRating()).isEqualTo(1734);
    assertThat(profile.stats().maxRating()).isEqualTo(1801);
    assertThat(profile.stats().rankLabel()).isEqualTo("3★");
    assertThat(profile.details().badges())
        .containsExactly(
            new PlatformDetails.Badge(
                "Problem Solver · Gold",
                null,
                "https://cdn.codechef.com/images/badges/problem/gold.svg",
                null));
    assertThat(profile.details().calendar()).containsExactly(java.util.Map.entry("2026-10-01", 4));
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void codeChefWithoutAProfileBlockIsNotFound() {
    assertThatThrownBy(() -> CodeChefReader.parse("<html>home page</html>"))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
  }

  @Test
  void geeksforGeeksGivesTheCountsByLevelAndFindsTheCodeInTheBio() throws Exception {
    PlatformProfile profile =
        GeeksforGeeksReader.parse(
            """
            {"message":"data retrieved successfully","data":{"name":"Demo","designation":null,
             "institute_name":"A College","total_problems_solved":6,"score":21}}
            """,
            """
            {"status":"success","result":{
              "School":{"1":{"lang":"python3","user_subtime":"2026-10-01 09:00:00"}},
              "Basic":{"2":{"lang":"python3","user_subtime":"2026-10-01 10:00:00"}},
              "Easy":{"3":{"lang":"java","user_subtime":"2026-10-02 10:00:00"},
                      "4":{"lang":"python3","user_subtime":"2026-10-03 10:00:00"}},
              "Medium":{"5":{"lang":"java","user_subtime":"2026-10-03 11:00:00"}},
              "Hard":{"6":{"lang":"java","user_subtime":"bad"}}}}
            """,
            "<script>self.__next_f.push([1,\"{\\\"bio\\\":\\\"Coder CC-ABCD2345\\\",\\\"headline\\\":\\\"\\\"}\"])</script>");

    assertThat(profile.stats().problemsSolved()).isEqualTo(6);
    assertThat(profile.stats().score()).isEqualTo(21);
    assertThat(profile.stats().easySolved()).isEqualTo(2);
    assertThat(profile.stats().mediumSolved()).isEqualTo(1);
    assertThat(profile.stats().hardSolved()).isEqualTo(1);
    assertThat(profile.details().basicSolved()).isEqualTo(2);
    assertThat(profile.details().calendar())
        .containsEntry("2026-10-01", 2)
        .containsEntry("2026-10-02", 1)
        .containsEntry("2026-10-03", 2);
    assertThat(profile.details().languages()).containsEntry("java", 3).containsEntry("python3", 3);
    // The code is only in the bio, which only the public page carries.
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void hackerRankAddsTheBadgesAndShowsTheProblemSolvingStars() throws Exception {
    PlatformProfile profile =
        HackerRankReader.parse(
            "{\"model\":{\"username\":\"demo\",\"short_bio\":\"CC-ABCD2345\",\"name\":\"Demo\"}}",
            """
            {"models":[{"badge_type":"problem-solving","badge_name":"Problem Solving","solved":30,"stars":4},
                       {"badge_type":"java","badge_name":"Java","solved":12,"stars":2},
                       {"badge_type":"sql","badge_name":"Sql","solved":0,"stars":0}]}
            """);

    assertThat(profile.stats().problemsSolved()).isEqualTo(42);
    assertThat(profile.stats().rankLabel()).isEqualTo("4★ Problem Solving");
    assertThat(profile.details().basicSolved()).isEqualTo(42);
    assertThat(profile.details().badges())
        .containsExactly(
            new PlatformDetails.Badge("Problem Solving", 4), new PlatformDetails.Badge("Java", 2));
    assertThat(profile.mentions("CC-ABCD2345")).isTrue();
  }

  @Test
  void anAnswerThatIsNotJsonMeansTheSiteIsUnavailable() {
    assertThatThrownBy(() -> GeeksforGeeksReader.parse("<html>busy</html>", "{}", ""))
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
