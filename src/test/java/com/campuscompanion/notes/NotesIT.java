package com.campuscompanion.notes;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.InMemoryStorageClient;
import com.campuscompanion.support.TestImages;
import com.campuscompanion.support.TestPdfs;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Notes (ADR 0014): writing and organising, folders, the Trash, attachments, links and sharing
 * between students. All data is synthetic. Each test uses its own students.
 */
class NotesIT extends ApiIntegrationTest {

  private static final String NOTES = "/api/v1/notes";
  private static final String FOLDERS = "/api/v1/notes/folders";
  private static final String FILES = "/api/v1/notes/attachments";

  @Autowired private InMemoryStorageClient storage;

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "NTE", 3, "A");
    return studentClient(rollNo);
  }

  private static String note(String title, String content) {
    return """
        {"title":"%s","content":"%s","source":"manual"}"""
        .formatted(title, content.replace("\"", "\\\""));
  }

  private long createNote(ApiTestClient who, String title, String content) throws Exception {
    HttpResponse<String> response = who.post(NOTES, note(title, content));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private JsonNode list(ApiTestClient who, String query) throws Exception {
    HttpResponse<String> response = who.get(NOTES + (query.isEmpty() ? "" : "?" + query));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json(response).get("notes");
  }

  private static List<Long> ids(JsonNode notes) {
    return notes.valueStream().map(n -> n.get("id").asLong()).toList();
  }

  private long createFolder(ApiTestClient who, String name) throws Exception {
    HttpResponse<String> response =
        who.post(FOLDERS, "{\"name\":\"" + name + "\",\"color\":\"red\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  // --- access ----------------------------------------------------------------------------------

  @Test
  void onlyALoggedInStudentCanUseNotes() throws Exception {
    assertProblem(visitor().get(NOTES), 401, "AUTH_REQUIRED");
    assertProblem(adminClient("notes-admin@university.example").get(NOTES), 403, "ACCESS_DENIED");
    assertProblem(adminClient("notes-admin@university.example").get(FOLDERS), 403, "ACCESS_DENIED");
    assertProblem(visitor().post(NOTES, note("x", "<p>y</p>")), 401, "AUTH_REQUIRED");
  }

  @Test
  void anotherStudentsNoteIsNeverFoundOrChanged() throws Exception {
    ApiTestClient owner = student("NTEA0001");
    ApiTestClient other = student("NTEA0002");
    long id = createNote(owner, "Private", "<p>mine</p>");

    assertProblem(other.get(NOTES + "/" + id), 404, "NOT_FOUND");
    assertProblem(other.patch(NOTES + "/" + id, "{\"title\":\"stolen\"}"), 404, "NOT_FOUND");
    assertProblem(other.delete(NOTES + "/" + id), 404, "NOT_FOUND");
    assertProblem(other.post(NOTES + "/" + id + "/duplicate", ""), 404, "NOT_FOUND");
    assertProblem(other.get(NOTES + "/" + id + "/share"), 404, "NOT_FOUND");
    assertThat(ids(list(other, ""))).doesNotContain(id);
    assertThat(json(owner.get(NOTES + "/" + id)).get("title").asString()).isEqualTo("Private");
  }

  // --- writing ---------------------------------------------------------------------------------

  @Test
  void aNoteIsCreatedSavedAndFoundAgain() throws Exception {
    ApiTestClient me = student("NTEB0001");
    long id = createNote(me, "Week 1", "<p>Intro to <strong>trees</strong></p>");
    assertThat(json(me.get(NOTES + "/" + id)).get("content").asString())
        .contains("<strong>trees</strong>");

    HttpResponse<String> saved =
        me.patch(
            NOTES + "/" + id,
            "{\"content\":\"<p>Intro to graphs</p>\",\"tags\":[\"ds\",\"ds\",\" \"]}");
    assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
    JsonNode note = json(saved).get("note");
    assertThat(note.get("content").asString()).isEqualTo("<p>Intro to graphs</p>");
    assertThat(note.get("tags")).hasSize(1);
    assertThat(list(me, "tag=ds")).hasSize(1);
    assertThat(list(me, "tag=other")).isEmpty();
  }

  @Test
  void aNoteNeedsATitleOrContent() throws Exception {
    ApiTestClient me = student("NTEB0002");
    assertProblem(me.post(NOTES, note("", "<p></p>")), 400, "VALIDATION_FAILED");
    assertProblem(me.post(NOTES, "{\"title\":\"   \",\"content\":\"\"}"), 400, "VALIDATION_FAILED");
    assertThat(me.post(NOTES, note("", "<p>only content</p>")).statusCode()).isEqualTo(201);
    assertThat(me.post(NOTES, note("only title", "")).statusCode()).isEqualTo(201);
    assertProblem(
        me.post(NOTES, "{\"title\":\"" + "t".repeat(201) + "\",\"content\":\"x\"}"),
        400,
        "VALIDATION_FAILED");
  }

  @Test
  void anEditThatEmptiesANoteDeletesItInsteadOfSavingIt() throws Exception {
    ApiTestClient me = student("NTEB0003");
    long id = createNote(me, "", "<p>draft</p>");
    HttpResponse<String> response = me.patch(NOTES + "/" + id, "{\"content\":\"<p></p>\"}");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response).get("deleted").asBoolean()).isTrue();
    assertProblem(me.get(NOTES + "/" + id), 404, "NOT_FOUND");
  }

  @Test
  void contentIsMadeSafeOnEveryWrite() throws Exception {
    ApiTestClient me = student("NTEB0004");
    long id =
        createNote(
            me,
            "Unsafe",
            "<p onclick='x()'>a</p><script>alert(1)</script><img src='https://evil.example/p.gif'>"
                + "<a href='javascript:alert(1)'>l</a><iframe src='https://evil.example'></iframe>");
    String stored = json(me.get(NOTES + "/" + id)).get("content").asString();
    assertThat(stored)
        .doesNotContain("script", "onclick", "evil.example", "javascript:", "iframe")
        .contains("<p>a</p>");
    String patched =
        json(me.patch(NOTES + "/" + id, "{\"content\":\"<p>b</p><script>1</script>\"}"))
            .get("note")
            .get("content")
            .asString();
    assertThat(patched).isEqualTo("<p>b</p>");
  }

  @Test
  void aLongNoteIsSavedAndAnOverlongOneIsRefusedNotCut() throws Exception {
    ApiTestClient me = student("NTEB0005");
    String long150k = "<p>" + "word ".repeat(30_000) + "</p>";
    long id = createNote(me, "Long", long150k);
    assertThat(json(me.get(NOTES + "/" + id)).get("content").asString()).hasSize(long150k.length());

    String tooLong = "<p>" + "x".repeat(200_001) + "</p>";
    HttpResponse<String> response = me.patch(NOTES + "/" + id, "{\"content\":\"" + tooLong + "\"}");
    assertProblem(response, 400, "VALIDATION_FAILED");
    assertThat(json(me.get(NOTES + "/" + id)).get("content").asString()).hasSize(long150k.length());
  }

  // --- views, search, sort ---------------------------------------------------------------------

  @Test
  void viewsFilterBySearchPinArchiveAndTrash() throws Exception {
    ApiTestClient me = student("NTEC0001");
    long alpha = createNote(me, "Alpha", "<p>binary trees</p>");
    long beta = createNote(me, "Beta", "<p>100% sure_thing</p>");
    long gamma = createNote(me, "Gamma", "<p>linked lists</p>");

    me.patch(NOTES + "/" + beta, "{\"pinned\":true}");
    me.patch(NOTES + "/" + gamma, "{\"archived\":true}");
    me.delete(NOTES + "/" + alpha);

    assertThat(ids(list(me, ""))).containsExactly(beta);
    assertThat(ids(list(me, "view=pinned"))).containsExactly(beta);
    assertThat(ids(list(me, "view=archived"))).containsExactly(gamma);
    assertThat(ids(list(me, "view=trash"))).containsExactly(alpha);
    assertThat(list(me, "view=trash").get(0).get("deletedAt").isNull()).isFalse();
    assertThat(ids(list(me, "view=recent"))).containsExactly(beta);
    assertThat(ids(list(me, "view=nonsense"))).containsExactly(beta);

    // Search treats % and _ as plain text.
    assertThat(ids(list(me, "q=100%25"))).containsExactly(beta);
    assertThat(ids(list(me, "q=sure_thing"))).containsExactly(beta);
    assertThat(ids(list(me, "q=%25"))).containsExactly(beta);
    // The underscore is a plain character, not "any one character".
    assertThat(ids(list(me, "q=sure_thin_"))).isEmpty();
    assertThat(ids(list(me, "q=sure%5Fthing"))).containsExactly(beta);
    assertThat(ids(list(me, "q=zzz-nothing"))).isEmpty();
    // A search inside the Trash finds trashed notes only.
    assertThat(ids(list(me, "view=trash&q=BINARY"))).containsExactly(alpha);
  }

  @Test
  void pinnedNotesComeFirstAndSortsWork() throws Exception {
    ApiTestClient me = student("NTEC0002");
    long b = createNote(me, "b second", "<p>x</p>");
    long a = createNote(me, "a first", "<p>x</p>");
    long c = createNote(me, "c third", "<p>x</p>");
    me.patch(NOTES + "/" + c, "{\"pinned\":true}");

    assertThat(ids(list(me, "sort=alphabetical"))).containsExactly(c, a, b);
    assertThat(ids(list(me, "sort=created"))).containsExactly(c, a, b);
    me.patch(NOTES + "/" + c, "{\"pinned\":false}");
    assertThat(ids(list(me, "sort=alphabetical"))).containsExactly(a, b, c);
    assertThat(ids(list(me, "sort=created"))).containsExactly(c, a, b);
  }

  @Test
  void theAttachmentsFilterKeepsNotesWithAPictureOrAFile() throws Exception {
    ApiTestClient me = student("NTEC0003");
    createNote(me, "plain", "<p>nothing</p>");
    long upload =
        uploadId(me, "photo.png", "image/png", TestImages.pngWithTransparency(80, 80), null);
    long withPicture =
        createNote(me, "pic", "<p><img src='/api/v1/notes/attachments/" + upload + "/file'></p>");
    assertThat(ids(list(me, "attachments=true"))).containsExactly(withPicture);
  }

  // --- folders ---------------------------------------------------------------------------------

  @Test
  void aStudentStartsWithTheDefaultFolders() throws Exception {
    ApiTestClient me = student("NTED0001");
    JsonNode folders = json(me.get(FOLDERS)).get("folders");
    assertThat(texts(folders, "name"))
        .containsExactly("Quick Notes", "Lectures", "Assignments", "General");
    assertThat(folders.get(0).get("color").asString()).isEqualTo("cyan");
    assertThat(folders.get(0).get("icon").asString()).isEqualTo("bolt");
    // Asking again does not seed again.
    assertThat(json(me.get(FOLDERS)).get("folders")).hasSize(4);
  }

  @Test
  void foldersHaveExactCountsPreviewsAndNeverDeleteTheirNotes() throws Exception {
    ApiTestClient me = student("NTED0002");
    long folder = createFolder(me, "Algorithms");
    for (int i = 1; i <= 7; i++) {
      HttpResponse<String> made =
          me.post(
              NOTES,
              "{\"title\":\"Note "
                  + i
                  + "\",\"content\":\"<p>text "
                  + i
                  + "</p>\",\"folderId\":"
                  + folder
                  + "}");
      assertThat(made.statusCode()).isEqualTo(201);
    }
    long trashed = createNoteIn(me, folder, "Trashed");
    me.delete(NOTES + "/" + trashed);
    long archived = createNoteIn(me, folder, "Archived");
    me.patch(NOTES + "/" + archived, "{\"archived\":true}");

    JsonNode card = folderNode(me, folder);
    assertThat(card.get("noteCount").asInt()).isEqualTo(7);
    assertThat(card.get("previews")).hasSize(5);
    assertThat(card.get("previews").get(0).get("snippet").asString()).startsWith("text");
    assertThat(ids(list(me, "folder=" + folder))).hasSize(7);

    // Deleting the folder unfiles its notes.
    assertThat(me.delete(FOLDERS + "/" + folder).statusCode()).isEqualTo(204);
    assertThat(ids(list(me, "folder=unfiled"))).hasSize(7);
    assertProblem(me.delete(FOLDERS + "/" + folder), 404, "NOT_FOUND");
  }

  private long createNoteIn(ApiTestClient who, long folder, String title) throws Exception {
    HttpResponse<String> response =
        who.post(
            NOTES,
            "{\"title\":\"" + title + "\",\"content\":\"<p>x</p>\",\"folderId\":" + folder + "}");
    return json(response).get("id").asLong();
  }

  private JsonNode folderNode(ApiTestClient who, long id) throws Exception {
    for (JsonNode folder : json(who.get(FOLDERS)).get("folders")) {
      if (folder.get("id").asLong() == id) {
        return folder;
      }
    }
    throw new AssertionError("folder " + id + " not listed");
  }

  @Test
  void folderNamesAreUniqueAndColoursAreChecked() throws Exception {
    ApiTestClient me = student("NTED0003");
    me.get(FOLDERS);
    long id = createFolder(me, "Unique");
    assertProblem(me.post(FOLDERS, "{\"name\":\"Unique\"}"), 409, "CONFLICT");
    assertProblem(me.post(FOLDERS, "{\"name\":\"   \"}"), 400, "VALIDATION_FAILED");
    assertProblem(
        me.patch(FOLDERS + "/" + id, "{\"color\":\"chartreuse\"}"), 400, "VALIDATION_FAILED");
    assertProblem(me.patch(FOLDERS + "/" + id, "{\"name\":\"General\"}"), 409, "CONFLICT");
    assertProblem(me.patch(FOLDERS + "/" + id, "{}"), 400, "VALIDATION_FAILED");
    JsonNode changed =
        json(
            me.patch(
                FOLDERS + "/" + id, "{\"name\":\"Renamed\",\"color\":\"teal\",\"icon\":\"code\"}"));
    assertThat(changed.get("name").asString()).isEqualTo("Renamed");
    assertThat(changed.get("color").asString()).isEqualTo("teal");
    assertThat(changed.get("icon").asString()).isEqualTo("code");
    // Another student may use the same name.
    ApiTestClient other = student("NTED0004");
    assertThat(other.post(FOLDERS, "{\"name\":\"Renamed\"}").statusCode()).isEqualTo(201);
  }

  @Test
  void aFolderCannotBeMovedIntoItselfOrItsChild() throws Exception {
    ApiTestClient me = student("NTED0005");
    long parent = createFolder(me, "Parent");
    long child = createFolder(me, "Child");
    assertThat(me.patch(FOLDERS + "/" + child, "{\"parentId\":" + parent + "}").statusCode())
        .isEqualTo(200);
    assertProblem(
        me.patch(FOLDERS + "/" + parent, "{\"parentId\":" + child + "}"), 400, "VALIDATION_FAILED");
    assertProblem(
        me.patch(FOLDERS + "/" + parent, "{\"parentId\":" + parent + "}"),
        400,
        "VALIDATION_FAILED");
    assertThat(
            json(me.patch(FOLDERS + "/" + child, "{\"toTopLevel\":true}")).get("parentId").isNull())
        .isTrue();
    // Another student's folder is never a parent.
    ApiTestClient other = student("NTED0006");
    long foreign = createFolder(other, "Foreign");
    assertThat(
            json(me.patch(FOLDERS + "/" + child, "{\"parentId\":" + foreign + "}"))
                .get("parentId")
                .isNull())
        .isTrue();
  }

  @Test
  void foldersAreReorderedInOneRequest() throws Exception {
    ApiTestClient me = student("NTED0007");
    long a = createFolder(me, "Order A");
    long b = createFolder(me, "Order B");
    HttpResponse<String> response =
        me.patch(
            FOLDERS,
            "{\"order\":[{\"id\":%d,\"position\":-2},{\"id\":%d,\"position\":-1,\"parentId\":%d,\"setParent\":true}]}"
                .formatted(b, a, b));
    // a cannot be placed under b and then b under a; each entry is applied on its own.
    assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
    List<String> names = texts(json(me.get(FOLDERS)).get("folders"), "name");
    assertThat(names.indexOf("Order B")).isLessThan(names.indexOf("Order A"));
    assertProblem(me.patch(FOLDERS, "{\"order\":[]}"), 400, "VALIDATION_FAILED");
  }

  @Test
  void aStudentWhoseFirstActionIsAQuickNoteStillGetsTheDefaultFolders() throws Exception {
    ApiTestClient me = student("NTED0011");
    HttpResponse<String> first =
        me.post(NOTES, "{\"content\":\"<p>first idea</p>\",\"source\":\"robot\"}");
    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(texts(json(me.get(FOLDERS)).get("folders"), "name"))
        .containsExactly("Quick Notes", "Lectures", "Assignments", "General");
  }

  @Test
  void aRobotNoteIsFiledIntoQuickNotesWhichIsCreatedAgainIfDeleted() throws Exception {
    ApiTestClient me = student("NTED0008");
    JsonNode folders = json(me.get(FOLDERS)).get("folders");
    long quick = folders.get(0).get("id").asLong();
    HttpResponse<String> first =
        me.post(NOTES, "{\"content\":\"<p>idea</p>\",\"source\":\"robot\"}");
    assertThat(json(first).get("folderId").asLong()).isEqualTo(quick);
    assertThat(json(first).get("source").asString()).isEqualTo("robot");

    me.delete(FOLDERS + "/" + quick);
    HttpResponse<String> second =
        me.post(NOTES, "{\"content\":\"<p>idea 2</p>\",\"source\":\"robot\"}");
    assertThat(json(second).get("folder").get("name").asString()).isEqualTo("Quick Notes");
    assertThat(json(second).get("folderId").asLong()).isNotEqualTo(quick);
    // A robot note can still choose a folder of its own.
    long own = createFolder(me, "Elsewhere");
    HttpResponse<String> third =
        me.post(NOTES, "{\"content\":\"<p>3</p>\",\"source\":\"robot\",\"folderId\":" + own + "}");
    assertThat(json(third).get("folderId").asLong()).isEqualTo(own);
  }

  @Test
  void aNoteCannotBeFiledIntoAnotherStudentsFolder() throws Exception {
    ApiTestClient me = student("NTED0009");
    ApiTestClient other = student("NTED0010");
    long foreign = createFolder(other, "Not yours");
    HttpResponse<String> response =
        me.post(NOTES, "{\"title\":\"x\",\"content\":\"<p>y</p>\",\"folderId\":" + foreign + "}");
    assertThat(json(response).get("folderId").isNull()).isTrue();
    long id = json(response).get("id").asLong();
    HttpResponse<String> moved = me.patch(NOTES + "/" + id, "{\"folderId\":" + foreign + "}");
    assertThat(json(moved).get("note").get("folderId").isNull()).isTrue();
  }

  // --- many at once, duplicate -----------------------------------------------------------------

  @Test
  void bulkActionsApplyOnlyToTheStudentsOwnNotes() throws Exception {
    ApiTestClient me = student("NTEE0001");
    ApiTestClient other = student("NTEE0002");
    long one = createNote(me, "One", "<p>1</p>");
    long two = createNote(me, "Two", "<p>2</p>");
    long theirs = createNote(other, "Theirs", "<p>3</p>");
    long folder = createFolder(me, "Bulk target");

    String both = "[" + one + "," + two + "," + theirs + "]";
    assertThat(
            json(me.post(NOTES + "/bulk", "{\"action\":\"pin\",\"ids\":" + both + "}"))
                .get("count")
                .asInt())
        .isEqualTo(2);
    assertThat(ids(list(me, "view=pinned"))).containsExactlyInAnyOrder(one, two);
    assertThat(list(other, "view=pinned")).isEmpty();

    me.post(
        NOTES + "/bulk", "{\"action\":\"move\",\"ids\":" + both + ",\"folderId\":" + folder + "}");
    assertThat(ids(list(me, "folder=" + folder))).containsExactlyInAnyOrder(one, two);
    me.post(NOTES + "/bulk", "{\"action\":\"move\",\"ids\":[" + one + "]}");
    assertThat(ids(list(me, "folder=unfiled"))).containsExactly(one);

    me.post(NOTES + "/bulk", "{\"action\":\"trash\",\"ids\":" + both + "}");
    assertThat(ids(list(me, "view=trash"))).containsExactlyInAnyOrder(one, two);
    me.post(NOTES + "/bulk", "{\"action\":\"restore\",\"ids\":[" + one + "]}");
    assertThat(ids(list(me, ""))).containsExactly(one);
    me.post(NOTES + "/bulk", "{\"action\":\"archive\",\"ids\":[" + one + "]}");
    assertThat(ids(list(me, "view=archived"))).containsExactly(one);
    me.post(NOTES + "/bulk", "{\"action\":\"unarchive\",\"ids\":[" + one + "]}");
    me.post(NOTES + "/bulk", "{\"action\":\"unpin\",\"ids\":" + both + "}");
    assertThat(list(me, "view=pinned")).isEmpty();

    // Permanent delete and empty-trash never touch anything of another student's.
    me.post(NOTES + "/bulk", "{\"action\":\"delete\",\"ids\":" + both + "}");
    assertThat(list(me, "view=trash")).isEmpty();
    assertThat(ids(list(other, ""))).containsExactly(theirs);

    long three = createNote(me, "Three", "<p>3</p>");
    me.delete(NOTES + "/" + three);
    assertThat(json(me.post(NOTES + "/bulk", "{\"action\":\"empty_trash\"}")).get("count").asInt())
        .isEqualTo(1);
    assertThat(ids(list(other, ""))).containsExactly(theirs);

    assertProblem(
        me.post(NOTES + "/bulk", "{\"action\":\"explode\",\"ids\":[1]}"), 400, "VALIDATION_FAILED");
    assertProblem(
        me.post(NOTES + "/bulk", "{\"action\":\"pin\",\"ids\":[]}"), 400, "VALIDATION_FAILED");
  }

  @Test
  void aTrashedNoteIsRestoredAndAPermanentDeleteIsFinal() throws Exception {
    ApiTestClient me = student("NTEE0003");
    long id = createNote(me, "Fragile", "<p>keep</p>");
    assertThat(me.delete(NOTES + "/" + id).statusCode()).isEqualTo(204);
    assertThat(list(me, "")).isEmpty();
    JsonNode restored = json(me.patch(NOTES + "/" + id, "{\"restore\":true}")).get("note");
    assertThat(restored.get("deletedAt").isNull()).isTrue();
    assertThat(ids(list(me, ""))).containsExactly(id);
    assertThat(me.delete(NOTES + "/" + id + "?permanent=true").statusCode()).isEqualTo(204);
    assertProblem(me.get(NOTES + "/" + id), 404, "NOT_FOUND");
  }

  @Test
  void aCopyHasItsOwnTitleFolderAndAttachmentsButNotThePinOrTheSharing() throws Exception {
    ApiTestClient me = student("NTEE0004");
    long folder = createFolder(me, "Copy folder");
    long file =
        uploadId(me, "photo.png", "image/png", TestImages.pngWithTransparency(70, 70), null);
    HttpResponse<String> made =
        me.post(
            NOTES,
            "{\"title\":\"Original\",\"folderId\":%d,\"content\":\"<p><img src=\\\"/api/v1/notes/attachments/%d/file\\\"></p>\"}"
                .formatted(folder, file));
    long original = json(made).get("id").asLong();
    me.patch(NOTES + "/" + original, "{\"pinned\":true}");
    me.put(NOTES + "/" + original + "/share", "{\"linkAccess\":\"view\"}");

    JsonNode copy = json(me.post(NOTES + "/" + original + "/duplicate", ""));
    assertThat(copy.get("title").asString()).isEqualTo("Original copy");
    assertThat(copy.get("folderId").asLong()).isEqualTo(folder);
    assertThat(copy.get("pinned").asBoolean()).isFalse();
    assertThat(copy.get("shared").asBoolean()).isFalse();
    String copyContent = copy.get("content").asString();
    assertThat(copyContent)
        .contains("/api/v1/notes/attachments/")
        .doesNotContain("attachments/" + file + "/");

    // Deleting the original for good leaves the copy's picture readable.
    me.delete(NOTES + "/" + original + "?permanent=true");
    long copyFile = Long.parseLong(copyContent.replaceAll(".*attachments/(\\d+)/file.*", "$1"));
    assertThat(me.get(FILES + "/" + copyFile + "/file").statusCode()).isEqualTo(302);
  }

  // --- attachments -----------------------------------------------------------------------------

  private long uploadId(ApiTestClient who, String name, String type, byte[] bytes, Long noteId)
      throws Exception {
    HttpResponse<String> response =
        who.postFile(FILES + (noteId == null ? "" : "?noteId=" + noteId), name, type, bytes);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  @Test
  void imagesAndFilesAreStoredListedAndServedThroughASignedLink() throws Exception {
    ApiTestClient me = student("NTEF0001");
    byte[] picture = TestImages.pngWithTransparency(90, 90);
    HttpResponse<String> uploaded = me.postFile(FILES, "../evil\\name.png", "image/png", picture);
    assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(201);
    JsonNode image = json(uploaded);
    assertThat(image.get("kind").asString()).isEqualTo("image");
    assertThat(image.get("mimeType").asString()).isEqualTo("image/jpeg");
    assertThat(image.get("name").asString())
        .doesNotContain("/")
        .doesNotContain("\\")
        .endsWith(".jpg");
    assertThat(image.get("url").asString())
        .isEqualTo("/api/v1/notes/attachments/" + image.get("id").asLong() + "/file");
    String stored = storage.lastWritten();
    assertThat(storage.paths()).contains(stored);
    assertThat(stored).startsWith("notes/").endsWith(".jpg").doesNotContain("evil");
    // What is stored is a new JPEG, not the file that was uploaded.
    assertThat(storage.contentAt(stored)[0]).isEqualTo((byte) 0xFF);
    assertThat(storage.contentAt(stored)[1]).isEqualTo((byte) 0xD8);

    long pdf = uploadId(me, "notes.pdf", "application/pdf", TestPdfs.valid(1), null);
    long second = uploadId(me, "second.pdf", "application/pdf", TestPdfs.valid(2), null);

    JsonNode all = json(me.get(FILES)).get("attachments");
    assertThat(all).hasSize(3);
    assertThat(json(me.get(FILES + "?kind=image")).get("attachments")).hasSize(1);
    assertThat(json(me.get(FILES + "?kind=file")).get("attachments")).hasSize(2);

    HttpResponse<String> redirect = me.get(FILES + "/" + image.get("id").asLong() + "/file");
    assertThat(redirect.statusCode()).isEqualTo(302);
    assertThat(redirect.headers().firstValue("Location").orElse(""))
        .startsWith("https://storage.example/signed/notes/");
    assertThat(redirect.headers().firstValue("Cache-Control").orElse("")).contains("private");
    HttpResponse<String> download = me.get(FILES + "/" + pdf + "/file?download=true");
    assertThat(download.headers().firstValue("Location").orElse("")).contains("download=notes.pdf");
    assertThat(second).isPositive();
  }

  @Test
  void aPictureIsTurnedUprightAndStripsItsHiddenData() throws Exception {
    ApiTestClient me = student("NTEF0007");
    byte[] sideways = TestImages.withExif(TestImages.jpeg(120, 60), 6, "GPS 12.97,77.59");
    HttpResponse<String> response = me.postFile(FILES, "phone.jpg", "image/jpeg", sideways);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    byte[] stored = storage.contentAt(storage.lastWritten());
    java.awt.image.BufferedImage picture = TestImages.read(stored);
    // Orientation 6 means a quarter turn, so the stored picture is taller than wide.
    assertThat(picture.getHeight()).isEqualTo(120);
    assertThat(picture.getWidth()).isEqualTo(60);
    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("GPS 12.97");
  }

  @Test
  void anUploadIsCheckedByTypeContentAndSize() throws Exception {
    ApiTestClient me = student("NTEF0002");
    byte[] picture = TestImages.pngWithTransparency(70, 70);
    assertProblem(
        me.postFile(
            FILES,
            "a.svg",
            "image/svg+xml",
            "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "a.exe", "application/x-msdownload", picture),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "fake.png", "image/png", "not a picture".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "fake.jpg", "image/jpeg", picture), 415, "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "fake.pdf", "application/pdf", TestPdfs.signatureOnly()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "locked.pdf", "application/pdf", TestPdfs.passwordProtected()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(
            FILES,
            "fake.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "plain".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "binary.txt", "text/plain", new byte[] {1, 2, 0, 3}),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "empty.png", "image/png", new byte[0]), 415, "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(FILES, "huge.pdf", "application/pdf", TestPdfs.tooLarge()),
        413,
        "UPLOAD_TOO_LARGE");
    assertThat(json(me.get(FILES)).get("attachments")).isEmpty();
  }

  @Test
  void anAttachmentBelongsToItsOwner() throws Exception {
    ApiTestClient me = student("NTEF0003");
    ApiTestClient other = student("NTEF0004");
    long id = uploadId(me, "mine.png", "image/png", TestImages.pngWithTransparency(70, 70), null);
    assertProblem(other.get(FILES + "/" + id + "/file"), 404, "NOT_FOUND");
    assertProblem(other.delete(FILES + "/" + id), 404, "NOT_FOUND");
    assertThat(json(other.get(FILES)).get("attachments")).isEmpty();
    // A forged note ID leaves the upload unattached instead of attaching it to someone else's note.
    long foreignNote = createNote(me, "Mine", "<p>x</p>");
    long forged =
        uploadId(other, "x.png", "image/png", TestImages.pngWithTransparency(70, 70), foreignNote);
    assertThat(json(other.get(FILES)).get("attachments").get(0).get("noteId").isNull()).isTrue();
    assertThat(forged).isPositive();
  }

  @Test
  void aNoteAdoptsTheUploadsItReferencesAndRemovesThemWhenDestroyed() throws Exception {
    ApiTestClient me = student("NTEF0005");
    long id = uploadId(me, "doc.pdf", "application/pdf", TestPdfs.valid(1), null);
    String stored = storage.lastWritten();
    long note =
        createNote(
            me,
            "With file",
            "<p><a href='/api/v1/notes/attachments/"
                + id
                + "/file' data-note-file>doc.pdf</a></p>");
    JsonNode row = json(me.get(FILES)).get("attachments").get(0);
    assertThat(row.get("noteId").asLong()).isEqualTo(note);
    assertThat(row.get("noteTitle").asString()).isEqualTo("With file");

    // In the Trash the file is kept, so a restored note still shows it.
    me.delete(NOTES + "/" + note);
    assertThat(storage.paths()).contains(stored);
    me.delete(NOTES + "/" + note + "?permanent=true");
    assertThat(storage.paths()).doesNotContain(stored);
    assertThat(json(me.get(FILES)).get("attachments")).isEmpty();
  }

  @Test
  void deletingAnAttachmentRemovesTheFileAndKeepsTheNoteText() throws Exception {
    ApiTestClient me = student("NTEF0006");
    long id = uploadId(me, "gone.png", "image/png", TestImages.pngWithTransparency(70, 70), null);
    String stored = storage.lastWritten();
    long note =
        createNote(
            me, "Keeps text", "<p>text <img src='/api/v1/notes/attachments/" + id + "/file'></p>");
    assertThat(me.delete(FILES + "/" + id).statusCode()).isEqualTo(204);
    assertThat(storage.paths()).doesNotContain(stored);
    assertThat(json(me.get(NOTES + "/" + note)).get("content").asString()).contains("text");
  }

  // --- links -----------------------------------------------------------------------------------

  @Test
  void aNoteLinksToOtherNotesAndFoldersByIdAndTheLabelFollowsTheTarget() throws Exception {
    ApiTestClient me = student("NTEG0001");
    ApiTestClient other = student("NTEG0002");
    long source = createNote(me, "Source", "<p>s</p>");
    long target = createNote(me, "Target note", "<p>t</p>");
    long folder = createFolder(me, "Target folder");
    long foreign = createNote(other, "Foreign", "<p>f</p>");

    JsonNode links =
        json(me.post(
                NOTES + "/" + source + "/links",
                "{\"targetType\":\"note\",\"targetId\":" + target + "}"))
            .get("links");
    assertThat(links).hasSize(1);
    assertThat(links.get(0).get("target").get("label").asString()).isEqualTo("Target note");
    me.post(
        NOTES + "/" + source + "/links", "{\"targetType\":\"folder\",\"targetId\":" + folder + "}");
    // Linking twice changes nothing.
    assertThat(
            json(me.post(
                    NOTES + "/" + source + "/links",
                    "{\"targetType\":\"note\",\"targetId\":" + target + "}"))
                .get("links"))
        .hasSize(2);

    assertProblem(
        me.post(
            NOTES + "/" + source + "/links",
            "{\"targetType\":\"note\",\"targetId\":" + source + "}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        me.post(
            NOTES + "/" + source + "/links",
            "{\"targetType\":\"note\",\"targetId\":" + foreign + "}"),
        404,
        "NOT_FOUND");
    assertProblem(
        me.post(NOTES + "/" + source + "/links", "{\"targetType\":\"opportunity\",\"targetId\":1}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        other.post(
            NOTES + "/" + source + "/links",
            "{\"targetType\":\"note\",\"targetId\":" + foreign + "}"),
        404,
        "NOT_FOUND");

    me.patch(NOTES + "/" + target, "{\"title\":\"Renamed target\"}");
    JsonNode again = json(me.get(NOTES + "/" + source + "/links")).get("links");
    assertThat(again.get(0).get("target").get("label").asString()).isEqualTo("Renamed target");

    // A deleted target stays in the list without a label.
    me.delete(NOTES + "/" + target + "?permanent=true");
    JsonNode after = json(me.get(NOTES + "/" + source + "/links")).get("links");
    assertThat(after.get(0).get("target").get("label").isNull()).isTrue();

    long linkId = after.get(0).get("id").asLong();
    assertThat(json(me.delete(NOTES + "/" + source + "/links/" + linkId)).get("links")).hasSize(1);
  }

  @Test
  void theLinkPickerSearchesOnlyTheStudentsOwnNotesAndFolders() throws Exception {
    ApiTestClient me = student("NTEG0003");
    ApiTestClient other = student("NTEG0004");
    long mine = createNote(me, "Quantum mechanics", "<p>x</p>");
    createNote(other, "Quantum secrets", "<p>x</p>");
    createFolder(me, "Quantum folder");

    JsonNode targets = json(me.get(NOTES + "/link-targets?q=quant")).get("targets");
    assertThat(texts(targets, "label"))
        .contains("Quantum mechanics", "Quantum folder")
        .doesNotContain("Quantum secrets");
    assertThat(
            json(me.get(NOTES + "/link-targets?q=quant&exclude=" + mine))
                .get("targets")
                .valueStream()
                .map(t -> t.get("label").asString())
                .toList())
        .doesNotContain("Quantum mechanics");
    assertThat(json(me.get(NOTES + "/link-targets?q=q")).get("targets")).isEmpty();
  }

  // --- sharing ---------------------------------------------------------------------------------

  @Test
  void aNoteCanBeOpenedThroughItsLinkOnlyWhileTheLinkIsOnAndOnlyBySignedInStudents()
      throws Exception {
    ApiTestClient owner = student("NTEH0001");
    ApiTestClient reader = student("NTEH0002");
    long id = createNote(owner, "Shared by link", "<p>hello</p>");
    assertThat(json(owner.get(NOTES + "/" + id + "/share")).get("share").isNull()).isTrue();

    JsonNode share =
        json(owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}")).get("share");
    String slug = share.get("slug").asString();
    assertThat(slug).hasSize(32);

    HttpResponse<String> opened = reader.get(NOTES + "/shared/" + slug);
    assertThat(opened.statusCode()).as(opened.body()).isEqualTo(200);
    JsonNode seen = json(opened);
    assertThat(seen.get("content").asString()).isEqualTo("<p>hello</p>");
    assertThat(seen.get("sharedPermission").asString()).isEqualTo("view");
    assertThat(seen.get("sharedBy").asString()).isNotBlank();
    // Reading through the link gives no way to edit.
    assertProblem(reader.patch(NOTES + "/" + id, "{\"title\":\"hijack\"}"), 404, "NOT_FOUND");
    assertProblem(visitor().get(NOTES + "/shared/" + slug), 401, "AUTH_REQUIRED");
    assertProblem(
        adminClient("share-admin@university.example").get(NOTES + "/shared/" + slug),
        403,
        "ACCESS_DENIED");

    assertThat(json(owner.get(NOTES + "/" + id)).get("shared").asBoolean()).isTrue();
    owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"private\"}");
    assertProblem(reader.get(NOTES + "/shared/" + slug), 404, "NOT_FOUND");
    // Turning it on again brings the same address back.
    assertThat(
            json(owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}"))
                .get("share")
                .get("slug")
                .asString())
        .isEqualTo(slug);
    assertThat(reader.get(NOTES + "/shared/" + slug).statusCode()).isEqualTo(200);
    owner.delete(NOTES + "/" + id);
    assertProblem(reader.get(NOTES + "/shared/" + slug), 404, "NOT_FOUND");
    assertProblem(reader.get(NOTES + "/shared/doesnotexist"), 404, "NOT_FOUND");
    assertProblem(
        owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"edit\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        reader.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}"), 404, "NOT_FOUND");
  }

  @Test
  void aNoteSharedWithAClassmateCanBeReadAndOnlyEditedWhenAllowed() throws Exception {
    ApiTestClient owner = student("NTEH0003");
    ApiTestClient viewer = student("NTEH0004");
    ApiTestClient editor = student("NTEH0005");
    ApiTestClient stranger = student("NTEH0006");
    long id = createNote(owner, "Group notes", "<p>v1</p>");

    // By roll number, in any case, and by college email.
    assertThat(
            owner
                .post(
                    NOTES + "/" + id + "/share/recipients",
                    "{\"identifier\":\"nteh0004\",\"permission\":\"view\"}")
                .statusCode())
        .isEqualTo(200);
    HttpResponse<String> byEmail =
        owner.post(
            NOTES + "/" + id + "/share/recipients",
            "{\"identifier\":\"nteh0005@university.example\",\"permission\":\"edit\"}");
    assertThat(byEmail.statusCode()).as(byEmail.body()).isEqualTo(200);
    JsonNode state = json(byEmail).get("share");
    assertThat(state.get("recipients")).hasSize(2);
    assertThat(texts(state.get("recipients"), "rollNo")).containsExactly("NTEH0004", "NTEH0005");
    // Sharing is no way to look up who is behind a roll number: no name comes back (CC-05).
    for (JsonNode person : state.get("recipients")) {
      assertThat(person.has("name")).isFalse();
    }
    assertThat(byEmail.body()).doesNotContain("Demo NTEH0004", "Demo NTEH0005");
    assertThat(owner.get(NOTES + "/" + id + "/share").body())
        .doesNotContain("Demo NTEH0004", "Demo NTEH0005");

    assertProblem(
        owner.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"nobody99\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        owner.post(
            NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"nteh0005@other.example\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        owner.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"NTEH0003\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        stranger.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"NTEH0006\"}"),
        404,
        "NOT_FOUND");

    // The "shared with me" view and the note itself.
    JsonNode shared = list(viewer, "view=shared");
    assertThat(ids(shared)).containsExactly(id);
    assertThat(shared.get(0).get("sharedPermission").asString()).isEqualTo("view");
    assertThat(json(viewer.get(NOTES + "/" + id)).get("title").asString()).isEqualTo("Group notes");
    assertThat(list(stranger, "view=shared")).isEmpty();
    assertProblem(stranger.get(NOTES + "/" + id), 404, "NOT_FOUND");
    assertThat(ids(list(viewer, ""))).doesNotContain(id);
    assertThat(list(viewer, "view=shared&q=nomatch")).isEmpty();

    // A viewer cannot change anything. An editor changes the text and nothing else.
    assertProblem(viewer.patch(NOTES + "/" + id, "{\"content\":\"<p>no</p>\"}"), 404, "NOT_FOUND");
    assertProblem(viewer.delete(NOTES + "/" + id), 404, "NOT_FOUND");
    HttpResponse<String> edited =
        editor.patch(
            NOTES + "/" + id, "{\"content\":\"<p>v2</p><script>x</script>\",\"pinned\":true}");
    assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
    assertThat(json(edited).get("note").get("content").asString()).isEqualTo("<p>v2</p>");
    assertThat(json(owner.get(NOTES + "/" + id)).get("pinned").asBoolean()).isFalse();
    assertProblem(editor.patch(NOTES + "/" + id, "{\"pinned\":true}"), 400, "VALIDATION_FAILED");
    assertProblem(
        editor.patch(NOTES + "/" + id, "{\"title\":\"\",\"content\":\"<p></p>\"}"),
        403,
        "ACCESS_DENIED");
    assertProblem(editor.delete(NOTES + "/" + id), 404, "NOT_FOUND");
    assertThat(json(owner.get(NOTES + "/" + id)).get("content").asString()).isEqualTo("<p>v2</p>");

    // The owner takes one classmate away, then stops sharing altogether.
    long editorId = state.get("recipients").get(1).get("recipientId").asLong();
    JsonNode afterRemove =
        json(owner.delete(NOTES + "/" + id + "/share/recipients/" + editorId)).get("share");
    assertThat(afterRemove.get("recipients")).hasSize(1);
    assertProblem(editor.get(NOTES + "/" + id), 404, "NOT_FOUND");
    JsonNode stopped = json(owner.delete(NOTES + "/" + id + "/share")).get("share");
    // A note that was only ever shared with named people has no link state to report.
    assertThat(stopped.isNull()).isTrue();
    assertProblem(viewer.get(NOTES + "/" + id), 404, "NOT_FOUND");
  }

  @Test
  void aClassmateWhoCanReadANoteCanSeeItsPicturesAndNobodyElseCan() throws Exception {
    ApiTestClient owner = student("NTEH0007");
    ApiTestClient classmate = student("NTEH0008");
    ApiTestClient stranger = student("NTEH0009");
    long picture =
        uploadId(owner, "shared.png", "image/png", TestImages.pngWithTransparency(70, 70), null);
    long id =
        createNote(
            owner,
            "With picture",
            "<p><img src='/api/v1/notes/attachments/" + picture + "/file'></p>");

    assertProblem(classmate.get(FILES + "/" + picture + "/file"), 404, "NOT_FOUND");
    owner.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"NTEH0008\"}");
    assertThat(classmate.get(FILES + "/" + picture + "/file").statusCode()).isEqualTo(302);
    assertProblem(stranger.get(FILES + "/" + picture + "/file"), 404, "NOT_FOUND");
    String slug =
        json(owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}"))
            .get("share")
            .get("slug")
            .asString();
    // The link opens the pictures only for a reader who presents it, never by number alone.
    assertProblem(stranger.get(FILES + "/" + picture + "/file"), 404, "NOT_FOUND");
    assertThat(stranger.get(FILES + "/" + picture + "/file?share=" + slug).statusCode())
        .isEqualTo(302);
    owner.delete(NOTES + "/" + id + "/share");
    assertProblem(stranger.get(FILES + "/" + picture + "/file?share=" + slug), 404, "NOT_FOUND");
  }

  @Test
  void anAttachmentNumberAloneNeverOpensALinkSharedNotesFile() throws Exception {
    ApiTestClient owner = student("NTEH0010");
    ApiTestClient stranger = student("NTEH0011");
    long picture =
        uploadId(owner, "secret.png", "image/png", TestImages.pngWithTransparency(40, 40), null);
    long file = uploadId(owner, "secret.pdf", "application/pdf", TestPdfs.valid(1), null);
    long id =
        createNote(
            owner,
            "Link shared",
            "<p><img src='/api/v1/notes/attachments/"
                + picture
                + "/file'><a href='/api/v1/notes/attachments/"
                + file
                + "/file'>pdf</a></p>");
    String slug =
        json(owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}"))
            .get("share")
            .get("slug")
            .asString();

    for (long attachment : new long[] {picture, file}) {
      String address = FILES + "/" + attachment + "/file";
      assertProblem(stranger.get(address), 404, "NOT_FOUND");
      assertProblem(stranger.get(address + "?share="), 404, "NOT_FOUND");
      assertProblem(stranger.get(address + "?share=" + "0".repeat(32)), 404, "NOT_FOUND");
      assertProblem(stranger.get(address + "?share=" + slug + "x"), 404, "NOT_FOUND");
      assertProblem(stranger.get(address + "?share=" + "a".repeat(5000)), 404, "NOT_FOUND");
      assertThat(stranger.get(address + "?share=" + slug).statusCode()).isEqualTo(302);
      assertThat(stranger.get(address + "?share=" + slug + "&download=true").statusCode())
          .isEqualTo(302);
    }
    // The owner needs no token.
    assertThat(owner.get(FILES + "/" + picture + "/file").statusCode()).isEqualTo(302);
  }

  @Test
  void aShareLinkOpensOnlyTheFilesOfItsOwnNote() throws Exception {
    ApiTestClient owner = student("NTEH0012");
    ApiTestClient stranger = student("NTEH0013");
    long sharedPicture =
        uploadId(owner, "a.png", "image/png", TestImages.pngWithTransparency(30, 30), null);
    long privatePicture =
        uploadId(owner, "b.png", "image/png", TestImages.pngWithTransparency(30, 30), null);
    long shared =
        createNote(
            owner,
            "Shared",
            "<p><img src='/api/v1/notes/attachments/" + sharedPicture + "/file'></p>");
    createNote(
        owner,
        "Private",
        "<p><img src='/api/v1/notes/attachments/" + privatePicture + "/file'></p>");
    String slug =
        json(owner.put(NOTES + "/" + shared + "/share", "{\"linkAccess\":\"view\"}"))
            .get("share")
            .get("slug")
            .asString();

    assertThat(stranger.get(FILES + "/" + sharedPicture + "/file?share=" + slug).statusCode())
        .isEqualTo(302);
    assertProblem(
        stranger.get(FILES + "/" + privatePicture + "/file?share=" + slug), 404, "NOT_FOUND");
  }

  @Test
  void theFilesOfANoteInTheTrashAreClosedToEveryoneButTheOwner() throws Exception {
    ApiTestClient owner = student("NTEH0014");
    ApiTestClient classmate = student("NTEH0015");
    ApiTestClient linkReader = student("NTEH0016");
    long picture =
        uploadId(owner, "t.png", "image/png", TestImages.pngWithTransparency(30, 30), null);
    long id =
        createNote(
            owner,
            "Soon trashed",
            "<p><img src='/api/v1/notes/attachments/" + picture + "/file'></p>");
    owner.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"NTEH0015\"}");
    String slug =
        json(owner.put(NOTES + "/" + id + "/share", "{\"linkAccess\":\"view\"}"))
            .get("share")
            .get("slug")
            .asString();
    String address = FILES + "/" + picture + "/file";
    assertThat(classmate.get(address).statusCode()).isEqualTo(302);
    assertThat(linkReader.get(address + "?share=" + slug).statusCode()).isEqualTo(302);

    owner.delete(NOTES + "/" + id);
    assertProblem(classmate.get(address), 404, "NOT_FOUND");
    assertProblem(linkReader.get(address + "?share=" + slug), 404, "NOT_FOUND");
    assertThat(owner.get(address).statusCode()).isEqualTo(302);

    owner.patch(NOTES + "/" + id, "{\"restore\":true}");
    assertThat(classmate.get(address).statusCode()).isEqualTo(302);
    assertThat(linkReader.get(address + "?share=" + slug).statusCode()).isEqualTo(302);
  }

  @Test
  void aPictureAddedByASharedEditorOpensForTheOwnerAndTheNotesReaders() throws Exception {
    ApiTestClient owner = student("NTEH0017");
    ApiTestClient editor = student("NTEH0018");
    ApiTestClient viewer = student("NTEH0019");
    ApiTestClient stranger = student("NTEH0020");
    long id = createNote(owner, "Group page", "<p>v1</p>");
    owner.post(
        NOTES + "/" + id + "/share/recipients",
        "{\"identifier\":\"NTEH0018\",\"permission\":\"edit\"}");
    owner.post(NOTES + "/" + id + "/share/recipients", "{\"identifier\":\"NTEH0019\"}");

    long picture =
        uploadId(editor, "editor.png", "image/png", TestImages.pngWithTransparency(40, 40), null);
    String editorFile = storage.lastWritten();
    long foreign =
        uploadId(
            stranger, "foreign.png", "image/png", TestImages.pngWithTransparency(40, 40), null);
    HttpResponse<String> saved =
        editor.patch(
            NOTES + "/" + id,
            "{\"content\":\"<p><img src='/api/v1/notes/attachments/"
                + picture
                + "/file'><img src='/api/v1/notes/attachments/"
                + foreign
                + "/file'></p>\"}");
    assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);

    String address = FILES + "/" + picture + "/file";
    assertThat(owner.get(address).statusCode()).isEqualTo(302);
    assertThat(viewer.get(address).statusCode()).isEqualTo(302);
    assertThat(editor.get(address).statusCode()).isEqualTo(302);
    assertProblem(stranger.get(address), 404, "NOT_FOUND");
    // The editor stays the uploader; the picture is now part of the note.
    JsonNode editorFiles = json(editor.get(FILES)).get("attachments");
    assertThat(texts(editorFiles, "url")).contains(address);

    // An editor cannot pull somebody else's upload into the note by naming it.
    assertProblem(owner.get(FILES + "/" + foreign + "/file"), 404, "NOT_FOUND");
    assertThat(stranger.get(FILES + "/" + foreign + "/file").statusCode()).isEqualTo(302);

    // Destroying the note destroys the picture with it.
    owner.delete(NOTES + "/" + id);
    owner.delete(NOTES + "/" + id + "?permanent=true");
    assertProblem(editor.get(address), 404, "NOT_FOUND");
    assertProblem(owner.get(address), 404, "NOT_FOUND");
    assertThat(storage.paths()).doesNotContain(editorFile);
  }
}
