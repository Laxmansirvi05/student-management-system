import { api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, stagger, tag, toast } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, swap } from "./motion.js";
import { cardHead, empty, loadInto, scoreKind } from "./parts.js";
import "./shell.js";

// Quick Exam (FR-QZ-001 to FR-QZ-003): set up a test, take it against the clock, read the result.
// The server picks the questions, sets when the test ends and marks it. The correct answers are
// not in the page until the test has been submitted: they arrive only with the result.

const content = document.getElementById("content");
const pageHead = document.getElementById("quiz-head");
const dialog = document.getElementById("submit-dialog");
const params = new URLSearchParams(location.search);
const LETTERS = ["A", "B", "C", "D"];
const LENGTHS = [5, 10, 20];
const SECONDS_PER_QUESTION = 60;
// A running test is remembered in this tab, so reloading the page continues it with the same
// questions and the same end time. It holds no answers key: only what the student chose.
const SAVED = "quick-exam";

const setup = { subjects: [], subjectId: Number(params.get("subjectId")) || 0, count: 10 };
let test = null;

const clock = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
const subjectOf = (id) => setup.subjects.find((subject) => subject.id === id);
/** "Unit 2" for an older unit test, "Whole subject" for a test of the whole subject. */
const unitLabel = (item) => (item.unitNo ? `Unit ${item.unitNo}` : "Whole subject");

// ---------------------------------------------------------------- Set up
function setupView(progress, recent) {
  const subjectSelect = h("select.select", { id: "quiz-subject" }, setup.subjects.map((subject) => h("option", { value: subject.id, selected: subject.id === setup.subjectId }, `${subject.name} (${subject.code})`)));
  const time = h("strong.mono.num.time-pill");
  const problem = h("div.inset.inset--warning.inset--note", { hidden: true, role: "alert" });
  const start = h("button.button.button--primary.button--block.button--large", { type: "button" }, "Start test", icon("arrow-right"));
  let pills = [];

  function showCount() {
    time.textContent = clock(setup.count * SECONDS_PER_QUESTION);
    for (const pill of pills) {
      pill.setAttribute("aria-pressed", String(Number(pill.textContent) === setup.count));
    }
    problem.hidden = true;
  }

  /** The subject has too few questions. Says how many, and offers the longest test that fits. */
  function notEnough(available) {
    const fits = LENGTHS.filter((length) => length <= available).pop();
    const offer = fits ? h("button.button.button--secondary.button--small", { type: "button" }, `Start with ${fits} questions`) : null;
    offer?.addEventListener("click", () => {
      setup.count = fits;
      showCount();
      begin();
    });
    const text =
      available === 0
        ? "This subject has no questions yet. Try another subject."
        : `This subject has only ${available} question${available === 1 ? "" : "s"} so far.` + (fits ? "" : " A test needs at least 5.");
    fill(problem, h("span.line.line--start", icon("alert"), h("span", text)), offer);
    problem.hidden = false;
  }

  async function begin() {
    start.disabled = true;
    try {
      const started = await api.post("/quiz-attempts", { subjectId: setup.subjectId, questionCount: setup.count });
      const subject = subjectOf(setup.subjectId);
      startTest(started, `${subject.name} · Whole subject`);
    } catch (failure) {
      if (failure.code === "QUIZ_NOT_ENOUGH_QUESTIONS") {
        notEnough(Number.isInteger(failure.available) ? failure.available : 0);
      } else {
        fill(problem, h("span.line.line--start", icon("alert"), h("span", messageFor(failure))));
        problem.hidden = false;
      }
      start.disabled = false;
    }
  }

  pills = LENGTHS.map((number) => {
    const pill = h("button.count-pill.num", { type: "button" }, number);
    pill.addEventListener("click", () => {
      setup.count = number;
      showCount();
    });
    return pill;
  });
  subjectSelect.addEventListener("change", () => {
    setup.subjectId = Number(subjectSelect.value);
    problem.hidden = true;
  });
  start.addEventListener("click", begin);
  showCount();

  const form = h(
    "section.card.reveal",
    { "aria-labelledby": "setup-title" },
    h("div.line.line--start", h("span.badge-icon.badge-icon--round", icon("play")), h("div.stack.stack--tight", h("h2.card__title", { id: "setup-title" }, "Start a quick test"), h("p.row__meta", "Choose a subject and how many questions. Each test draws new random questions from the subject's question bank."))),
    h("div.field", h("label.field__label", { for: "quiz-subject" }, "Subject"), subjectSelect),
    h("div.field", h("span.field__label", "Number of questions"), h("div.count-pills", { role: "group", "aria-label": "Number of questions" }, pills)),
    h("div.inset.inset--row", h("span.line.line--start", icon("timer"), h("span.stack.stack--tight", h("strong", "Time allowed"), h("span.row__meta", "1 minute per question. The test ends when the time runs out."))), time),
    problem,
    start,
  );
  return h("div.grid.grid--aside", form, h("div.stack.stack--loose", practiceCard(progress), recentCard(recent)));
}

function practiceCard(progress) {
  const figure = h("p.hero__figure");
  if (progress.averagePercent === null) {
    figure.textContent = "—";
  } else {
    countUp(figure, Math.round(progress.averagePercent), { suffix: "%" });
  }
  return h(
    "section.hero.reveal",
    { "data-tilt": true, "aria-label": "Your practice so far" },
    h("div.line", h("p.hero__eyebrow", "Your practice"), h("span.chip", progress.testsTaken === 1 ? "1 test" : progress.testsTaken + " tests")),
    h("div.stack.stack--tight", figure, h("p.hero__sub", progress.testsTaken ? "Average score" : "Take your first test to see your average")),
    h(
      "div.stack",
      progress.units.slice(0, 3).map((unit) =>
        h("div.stack.stack--tight", h("div.line", h("span.small.clip", `${unit.subjectCode} · ${unitLabel(unit)}`), h("span.mono.num", Math.round(unit.accuracyPercent) + "%")), bar(unit.accuracyPercent)),
      ),
    ),
  );
}

function recentCard(attempts) {
  const closed = attempts.filter((attempt) => attempt.status !== "IN_PROGRESS");
  return h(
    "section.card.reveal",
    cardHead("history", "Recent tests", "/student/quiz-history.html"),
    closed.length
      ? h(
          "ul.rows",
          closed.map((attempt) => {
            const kind = scoreKind((attempt.score / attempt.questionCount) * 100);
            return h(
              "li.row.attempt",
              h("span.badge-icon.badge-icon--round" + (kind ? ".badge-icon--" + kind : ".badge-icon--neutral"), icon(kind === "danger" ? "alert" : "check")),
              h("div.stack.stack--tight", h("span.row__title.clip", `${attempt.subjectName} · ${unitLabel(attempt)}`), h("span.row__meta.num", formatDate(attempt.startedAt))),
              h("strong.num.attempt__score" + (kind ? ".attempt__score--" + kind : ""), `${attempt.score}/${attempt.questionCount}`),
            );
          }),
        )
      : empty("history", "No tests yet", "Your tests appear here once you take one."),
  );
}

// ---------------------------------------------------------------- Taking the test
/** Keeps the running test in this tab, so a reload continues it. */
function remember() {
  const { attemptId, title, questions, answers, current, endsAt, total } = test;
  sessionStorage.setItem(SAVED, JSON.stringify({ attemptId, title, questions, answers, current, endsAt, total }));
}

function startTest(started, title) {
  // The end is counted from this device's own clock and the length the server gave, so a wrong
  // clock on the device cannot shorten or lengthen the test. The server keeps the real limit.
  runTest({
    attemptId: started.attemptId,
    title,
    questions: started.questions,
    answers: {},
    current: 0,
    total: started.durationSeconds,
    endsAt: Date.now() + started.durationSeconds * 1000,
  });
}

function runTest(saved) {
  test = { ...saved, timer: null, warned: false, sending: false };
  remember();
  pageHead.hidden = true;
  swap(content, () => {
    fill(content, testView());
    renderQuestion();
    tick();
    test.timer = setInterval(tick, 1000);
    window.scrollTo({ top: 0 });
  });
}

const secondsLeft = () => Math.max(0, Math.ceil((test.endsAt - Date.now()) / 1000));

function tick() {
  const pill = document.getElementById("timer");
  if (!pill || !test) {
    return;
  }
  const left = secondsLeft();
  document.getElementById("timer-text").textContent = clock(left);
  if (left <= 60 && !test.warned) {
    test.warned = true;
    pill.classList.add("timer--warning");
    document.getElementById("timer-alert").textContent = "One minute left.";
  }
  if (left <= 0) {
    finish();
  }
}

function testView() {
  return [
    h(
      "section.card.testbar",
      h("div.stack.stack--tight", h("p.eyebrow", test.title), h("p.testbar__where", { id: "where", "aria-live": "polite" })),
      h("div.testbar__progress", h("div.bar", h("div.bar__fill.bar__fill--live", { id: "progress-fill" }))),
      h("div.timer", { id: "timer" }, icon("timer"), h("span.mono.num", { id: "timer-text" }), h("span.visually-hidden", { id: "timer-alert", role: "status" })),
    ),
    h(
      "div.grid.grid--aside",
      h("section.card.question", { id: "question", "aria-live": "polite" }),
      h(
        "aside.card.sticky",
        h("div.stack.stack--tight", h("h2.card__title", "Questions"), h("p.row__meta", { id: "answered-text" })),
        h("div.qgrid", { id: "qgrid" }),
        h("ul.legend", h("li", h("span.qdot.qdot--answered", { "aria-hidden": "true" }), "Answered"), h("li", h("span.qdot", { "aria-hidden": "true" }), "Not answered")),
        h("button.button.button--inverse.button--block", { type: "button", onclick: askToSubmit }, icon("check-circle"), "Submit test"),
        h("p.note", icon("info"), "Keys A to D choose an answer. The arrow keys move between questions."),
      ),
    ),
  ];
}

const answeredCount = () => test.questions.filter((question) => test.answers[question.questionId]).length;

function renderQuestion() {
  const index = test.current;
  const question = test.questions[index];
  const answered = answeredCount();
  const chosenKey = test.answers[question.questionId];
  document.getElementById("where").textContent = `Question ${index + 1} of ${test.questions.length}`;
  document.getElementById("answered-text").textContent = `${answered} of ${test.questions.length} answered`;
  document.getElementById("progress-fill").style.setProperty("--value", String((answered / test.questions.length) * 100));

  // The letters name the places on the screen. What is sent to the server is the option's own key.
  const options = question.options.map((option, place) =>
    h(
      "button.option",
      { type: "button", "aria-pressed": String(option.key === chosenKey), onclick: () => answer(option.key) },
      h("span.option__letter", LETTERS[place]),
      h("span.option__text", option.text),
      h("span.option__check", icon("check")),
    ),
  );
  const previous = h("button.button.button--quiet", { type: "button", disabled: index === 0, onclick: () => go(index - 1) }, icon("arrow-left"), "Previous");
  const last = index === test.questions.length - 1;
  const next = h("button.button.button--primary", { type: "button", onclick: () => (last ? askToSubmit() : go(index + 1)) }, last ? "Finish" : "Next", icon(last ? "check" : "arrow-right"));
  const clear = h("button.button.button--text", { type: "button", hidden: !chosenKey, onclick: () => answer(null) }, "Clear answer");
  fill(
    document.getElementById("question"),
    h("p.eyebrow", `Question ${index + 1}`),
    h("h2.question__text", question.text),
    h("div.options", { role: "group", "aria-label": "Answers" }, options),
    h("div.question__actions", previous, clear, next),
  );

  fill(
    document.getElementById("qgrid"),
    test.questions.map((entry, number) => {
      const done = Boolean(test.answers[entry.questionId]);
      return h(
        "button.qdot.num" + (done ? ".qdot--answered" : "") + (number === index ? ".qdot--current" : ""),
        { type: "button", "aria-label": `Question ${number + 1}` + (done ? ", answered" : ", not answered"), onclick: () => go(number) },
        number + 1,
      );
    }),
  );
}

function answer(key) {
  const questionId = test.questions[test.current].questionId;
  if (key) {
    test.answers[questionId] = key;
  } else {
    delete test.answers[questionId];
  }
  remember();
  renderQuestion();
}

function go(index) {
  test.current = Math.max(0, Math.min(test.questions.length - 1, index));
  remember();
  swap(document.getElementById("question"), renderQuestion);
}

function askToSubmit() {
  const open = test.questions.map((question, index) => (test.answers[question.questionId] ? 0 : index + 1)).filter(Boolean);
  document.getElementById("submit-text").textContent = open.length
    ? `${open.length} question${open.length > 1 ? "s are" : " is"} unanswered (${open.join(", ")}). You cannot change your answers after submitting.`
    : "You have answered every question. You cannot change your answers after submitting.";
  document.getElementById("submit-done").textContent = `${test.questions.length - open.length} / ${test.questions.length}`;
  document.getElementById("submit-time").textContent = clock(secondsLeft());
  dialog.showModal();
}

dialog.addEventListener("click", (event) => {
  if (event.target === dialog || event.target.closest("[data-close]")) {
    dialog.close();
  }
});
document.getElementById("submit-confirm").addEventListener("click", () => {
  dialog.close();
  finish();
});

document.addEventListener("keydown", (event) => {
  if (!test || test.timer === null || dialog.open || event.target.closest?.("select, input, textarea")) {
    return;
  }
  const place = LETTERS.indexOf(event.key.toUpperCase());
  const options = test.questions[test.current].options;
  if (place >= 0 && place < options.length) {
    answer(options[place].key);
  } else if (event.key === "ArrowRight") {
    go(test.current + 1);
  } else if (event.key === "ArrowLeft") {
    go(test.current - 1);
  }
});

/** Sends the answers. The server marks them and answers with the result. */
async function finish() {
  if (!test || test.sending) {
    return;
  }
  test.sending = true;
  clearInterval(test.timer);
  test.timer = null;
  if (dialog.open) {
    dialog.close();
  }
  const attemptId = test.attemptId;
  const answers = test.questions.map((question) => ({ questionId: question.questionId, selectedOption: test.answers[question.questionId] || null }));
  try {
    const result = await api.post(`/quiz-attempts/${attemptId}/submit`, { answers });
    forget();
    showResult(result);
  } catch (failure) {
    if (failure.code === "QUIZ_ATTEMPT_EXPIRED" || failure.code === "QUIZ_ALREADY_SUBMITTED") {
      // The test is closed on the server. Show what the server has for it.
      forget();
      openResult(attemptId);
    } else {
      // Most likely the connection. The answers are still here, so the student can try again.
      test.sending = false;
      test.timer = setInterval(tick, 1000);
      toast(messageFor(failure) + " Your answers are kept. Try submitting again.");
    }
  }
}

function forget() {
  sessionStorage.removeItem(SAVED);
  test = null;
}

// ---------------------------------------------------------------- Result
function showResult(result) {
  pageHead.hidden = true;
  swap(content, () => {
    fill(content, resultView(result));
    reveal();
    enableTilt();
    window.scrollTo({ top: 0 });
  });
}

function stateOf(item) {
  if (!item.selectedOption) {
    return "open";
  }
  return item.correct ? "right" : "wrong";
}

const STATES = {
  right: ["Correct", "success", "check"],
  wrong: ["Incorrect", "danger", "x"],
  open: ["Not answered", "", "minus"],
};

const textOf = (item, key) => item.options.find((option) => option.key === key)?.text || "";

function resultView(result) {
  const items = result.items.map((item) => ({ ...item, state: stateOf(item) }));
  const countOf = (state) => items.filter((item) => item.state === state).length;
  const percentage = Math.round(result.percent);
  const expired = result.status === "EXPIRED";
  const figure = h("p.hero__figure", h("span", { id: "score" }, "0"), h("small", ` / ${result.questionCount}`));
  countUp(figure.firstChild, result.score);
  const seconds = result.submittedAt ? Math.round((new Date(result.submittedAt) - new Date(result.startedAt)) / 1000) : null;
  let filter = "all";

  const list = h("div.stack.stack--loose", { id: "review" });
  function renderReview() {
    fill(list, stagger(items.filter((item) => filter === "all" || item.state === filter).map(reviewCard)));
  }
  function reviewCard(item) {
    const [label, kind, iconName] = STATES[item.state];
    return h(
      "article.card.reveal",
      h("div.line", h("span.line.line--start", h("span.eyebrow.num", "Q" + item.position), tag(label, kind, iconName))),
      h("h3.question__text.question__text--small", item.text),
      h(
        "div.tiles.tiles--answers",
        h(
          "div.inset.answer" + (item.state === "wrong" ? ".answer--wrong" : item.state === "right" ? ".answer--right" : ""),
          h("p.eyebrow", "Your answer"),
          h("p.line", h("span", item.selectedOption ? textOf(item, item.selectedOption) : "None selected"), icon(item.state === "right" ? "check-circle" : item.state === "wrong" ? "x-circle" : "minus-circle")),
        ),
        h("div.inset.answer.answer--right", h("p.eyebrow", "Correct answer"), h("p.line", h("strong", textOf(item, item.correctOption)), icon("check-circle"))),
      ),
      h("p.note", icon("info"), h("span", h("strong", "Explanation: "), item.explanation)),
      h("div.line.line--start", askAi(item)),
    );
  }

  /** Opens the assistant with this question as the topic. The official explanation stays above. */
  function askAi(item) {
    const button = h("button.button.button--quiet.button--small", { type: "button" }, icon("sparkles"), "Ask AI to explain");
    button.addEventListener("click", () => {
      sessionStorage.setItem("assistant-prefill", JSON.stringify({ topic: item.text.slice(0, 200), subjectId: result.subjectId, unitId: result.unitId }));
      window.location.assign("/student/assistant.html");
    });
    return button;
  }

  const filters = h(
    "div.pills",
    { role: "group", "aria-label": "Show" },
    [["all", "All", items.length], ["right", "Correct", countOf("right")], ["wrong", "Incorrect", countOf("wrong")], ["open", "Not answered", countOf("open")]].map(([value, label, count]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(value === filter) }, label, h("span.pill__count", count));
      pill.addEventListener("click", () => {
        filter = value;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(list, renderReview);
      });
      return pill;
    }),
  );

  renderReview();
  const retake = `/student/quiz.html?subjectId=${result.subjectId}`;
  return [
    expired
      ? h("p.inset.inset--warning.inset--note.reveal", { role: "status" }, icon("timer"), "The time ran out before this test was submitted, so no answers were counted. The correct answers are below.")
      : null,
    h(
      "section.hero.hero--split.reveal",
      { "data-tilt": true, "aria-label": "Your score" },
      h(
        "div.hero__main",
        h("p.hero__eyebrow", expired ? "Test not submitted in time" : `Test completed · ${formatDate(result.startedAt)}`),
        h("h2.hero__title.hero__title--small", result.unitNo ? `${result.subjectName} · Unit ${result.unitNo}: ${result.unitTitle}` : `${result.subjectName} · Whole subject`),
        figure,
        h(
          "ul.chips",
          h("li.chip.chip--good", icon("check"), "Correct ", h("strong", countOf("right"))),
          h("li.chip.chip--bad", icon("x"), "Incorrect ", h("strong", countOf("wrong"))),
          h("li.chip", icon("minus"), "Not answered ", h("strong", countOf("open"))),
          seconds === null ? null : h("li.chip", icon("timer"), "Time ", h("strong", clock(seconds))),
        ),
        h("div.line.line--start.line--wrap", h("a.button.button--primary", { href: retake }, icon("refresh"), "Take another test"), h("a.button.button--glass", { href: "/student/progress.html" }, icon("progress"), "See your progress")),
      ),
      h("div.hero__aside", ring(percentage, { size: 168, stroke: 14, label: [h("strong.ring__big", percentage + "%"), h("span.eyebrow.eyebrow--inverse", "Score")] })),
    ),
    h("div.section-head.reveal", h("div.section-head__text", h("h2", "Review your answers"), h("p.row__meta", "The correct answer and a short explanation for every question.")), filters),
    list,
  ];
}

/** Opens the result of a closed test, for example from the history page. */
function openResult(attemptId) {
  pageHead.hidden = true;
  loadInto(content, () => api.get("/quiz-attempts/" + attemptId), (result) => {
    fill(content, resultView(result));
    reveal();
    enableTilt();
  });
}

// ---------------------------------------------------------------- Start
function openSetup() {
  loadInto(
    content,
    () => Promise.all([api.get("/subjects"), api.get("/progress/me"), api.get("/quiz-attempts/me?size=4")]),
    ([subjects, progress, history]) => {
      setup.subjects = subjects;
      if (!subjects.length) {
        fill(content, h("div.card", empty("quiz", "No subjects yet", "Quick tests appear here once your subjects are set up.")));
        return;
      }
      if (!subjectOf(setup.subjectId)) {
        setup.subjectId = subjects[0].id;
      }
      fill(content, setupView(progress, history.content));
      reveal();
      enableTilt();
    },
  );
}

/** A test that was running when the page was reloaded, or null. */
function savedTest() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(SAVED));
    return saved && saved.attemptId && Array.isArray(saved.questions) ? saved : null;
  } catch {
    return null;
  }
}

const saved = savedTest();
const attemptId = Number(params.get("attempt"));
if (saved) {
  // Reloading the page does not restart the timer: the test carries on towards the same end.
  runTest(saved);
} else if (attemptId > 0) {
  openResult(attemptId);
} else {
  openSetup();
}
