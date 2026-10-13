import { dataTable, field, hidePageError, input, numberSelect, openForm, pager, rowAction, select, textarea } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { fill, h, icon, tag, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: the question bank (FR-ADM-008). A question is written as a draft, published so that
// tests can use it, and retired when it should no longer appear. Nothing is deleted. Once a
// question has been used in a test, what it asks and its correct option can no longer change.

const OPTIONS = ["A", "B", "C", "D"];
const DIFFICULTIES = [["EASY", "Easy"], ["MEDIUM", "Medium"], ["HARD", "Hard"]];
const STATUSES = { DRAFT: ["Draft", "", "edit"], PUBLISHED: ["Published", "success", "eye"], RETIRED: ["Retired", "", "lock"] };
const content = document.getElementById("content");
const filters = { subjectId: "", unitId: "", status: "" };
let subjects = [];
let pageNumber = 0;

function subjectOptions(first) {
  return [["", first], ...subjects.map((subject) => [String(subject.id), `${subject.code} · ${subject.name}`])];
}

async function fillUnits(unitSelect, subjectId, first, chosen = "") {
  const blank = h("option", { value: "" }, first);
  fill(unitSelect, blank);
  if (!subjectId) {
    return;
  }
  try {
    const units = await api.get(`/subjects/${subjectId}/units`);
    fill(unitSelect, blank, units.map((unit) => h("option", { value: unit.id, selected: String(unit.id) === String(chosen) }, `Unit ${unit.unitNo}: ${unit.title}`)));
  } catch (failure) {
    toast(messageFor(failure));
  }
}

function statusChoices(question) {
  if (!question) {
    return [["DRAFT", "Draft: not used in tests yet"], ["PUBLISHED", "Published: tests can use it"]];
  }
  const all = [["DRAFT", "Draft"], ["PUBLISHED", "Published: tests can use it"], ["RETIRED", "Retired: no longer used"]];
  return question.used ? all.slice(1) : all;
}

function questionForm(question) {
  const locked = Boolean(question?.used);
  const lock = locked ? { disabled: true } : {};
  const dialog = openForm({
    title: question ? "Edit question" : "Add a question",
    intro: locked
      ? "This question has been used in a test, so its text, options and correct option are fixed. You can still improve the explanation, or retire it and add a corrected question."
      : "Four options, one of them correct. Students see the options in a random order.",
    submitLabel: "Save",
    wide: true,
    fields: [
      field("Subject", numberSelect("subjectId", subjectOptions("Choose a subject"), question?.subjectId || filters.subjectId)),
      field("Unit", numberSelect("unitId", [["", "Choose a unit"]])),
      h("div.field--wide", field("Question", textarea("questionText", { maxlength: 1000, ...lock }))),
      ...OPTIONS.map((letter) => field("Option " + letter, input("option" + letter, { value: question?.["option" + letter] || "", maxlength: 300, ...lock }))),
      field("Correct option", select("correctOption", OPTIONS.map((letter) => [letter, "Option " + letter]), question?.correctOption || "A")),
      field("Difficulty", select("difficulty", DIFFICULTIES, question?.difficulty || "MEDIUM")),
      h("div.field--wide", field("Explanation", textarea("explanation", { maxlength: 2000 }), "Shown with the result, after the test. Say why the correct option is right.")),
      field("Status", select("status", statusChoices(question), question?.status || "DRAFT")),
    ],
    onSubmit: async (values) => {
      if (question) {
        await api.put("/admin/quiz-questions/" + question.id, values);
      } else {
        await api.post("/admin/quiz-questions", values);
      }
      toast("Saved");
      load();
    },
  });
  dialog.querySelector('[name="questionText"]').value = question?.questionText || "";
  dialog.querySelector('[name="explanation"]').value = question?.explanation || "";

  const subject = dialog.querySelector('[name="subjectId"]');
  const unit = dialog.querySelector('[name="unitId"]');
  subject.addEventListener("change", () => fillUnits(unit, subject.value, "Choose a unit"));
  fillUnits(unit, subject.value, "Choose a unit", question?.unitId || filters.unitId);
  if (locked) {
    for (const control of [subject, unit, dialog.querySelector('[name="correctOption"]')]) {
      control.disabled = true;
    }
  }
}

/** Everything the server needs to save a question again, with a new status. */
function withStatus(question, status) {
  const { subjectId, unitId, questionText, optionA, optionB, optionC, optionD, correctOption, explanation, difficulty } = question;
  return { subjectId, unitId, questionText, optionA, optionB, optionC, optionD, correctOption, explanation, difficulty, status };
}

async function setStatus(question, status, done) {
  try {
    await api.put("/admin/quiz-questions/" + question.id, withStatus(question, status));
    toast(done);
    load();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

function statusAction(question) {
  if (question.status === "PUBLISHED") {
    return rowAction("Retire: " + question.questionText, "lock", () => setStatus(question, "RETIRED", "Retired"));
  }
  return rowAction("Publish: " + question.questionText, "send", () => setStatus(question, "PUBLISHED", "Published"));
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ size: 20, page: pageNumber });
  for (const [name, value] of Object.entries(filters)) {
    if (value) {
      params.set(name, value);
    }
  }
  loadInto(list, () => api.get("/admin/quiz-questions?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 question" : page.totalElements + " questions"),
      dataTable(
        [
          {
            label: "Question",
            cell: (question) =>
              h("div.stack.stack--tight", h("strong.clamp-2", question.questionText), h("span.row__meta", `Correct: ${question.correctOption}. ${question["option" + question.correctOption]}`)),
          },
          { label: "Unit", cell: (question) => h("span.num", `${question.subjectCode} · Unit ${question.unitNo}`) },
          { label: "Difficulty", cell: (question) => DIFFICULTIES.find(([value]) => value === question.difficulty)?.[1] || question.difficulty },
          {
            label: "Status",
            cell: (question) => {
              const [label, kind, iconName] = STATUSES[question.status];
              return h("span.line.line--start.line--wrap", tag(label, kind, iconName), question.used ? tag("Used in a test", "primary") : null);
            },
          },
          { label: "Actions", align: "end", cell: (question) => h("span.row-actions", statusAction(question), rowAction("Edit: " + question.questionText, "edit", () => questionForm(question))) },
        ],
        page.content,
        filters.subjectId || filters.unitId || filters.status ? "No questions match these filters." : "No questions yet. Add the first one with the button above.",
      ),
      pager(page, (target) => {
        pageNumber = target;
        load();
      }),
    );
  });
}

function filterBar() {
  const subject = numberSelect("subject-filter", subjectOptions("All subjects"));
  const unit = numberSelect("unit-filter", [["", "All units"]]);
  const status = select("status-filter", [["", "Any status"], ["DRAFT", "Draft"], ["PUBLISHED", "Published"], ["RETIRED", "Retired"]]);
  const changed = () => {
    pageNumber = 0;
    load();
  };
  subject.addEventListener("change", () => {
    filters.subjectId = subject.value;
    filters.unitId = "";
    fillUnits(unit, subject.value, "All units");
    changed();
  });
  unit.addEventListener("change", () => {
    filters.unitId = unit.value;
    changed();
  });
  status.addEventListener("change", () => {
    filters.status = status.value;
    changed();
  });
  return h("section.card.toolbar-card", { "aria-label": "Filters" }, h("div.filters-row", field("Subject", subject), field("Unit", unit), field("Status", status)));
}

function start(loaded) {
  subjects = loaded.filter((subject) => subject.active);
  if (!subjects.length) {
    fill(content, h("div.card", empty("resources", "No subjects yet", "Add a subject and its units before writing questions.", h("a.button.button--primary.button--small", { href: "/admin/subjects.html" }, "Go to subjects"))));
    return;
  }
  const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "Add question");
  add.addEventListener("click", () => questionForm(null));
  fill(document.getElementById("actions"), add);
  fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Questions" }, h("div.skeleton")));
  load();
}

loadInto(content, () => api.get("/admin/subjects"), start);
