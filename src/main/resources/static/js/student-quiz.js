import { fill, formatDate, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, swap } from "./motion.js";
import { cardHead, scoreKind } from "./parts.js";
import { progress, quiz } from "./sample-data.js";
import "./shell.js";

// Quick Exam (FR-QZ-001 to FR-QZ-003): set up a test, take it against the clock, read the result.
// In the finished feature the server picks the questions, keeps the time and marks the test.
// Here the three screens run on sample questions so the design can be seen and tried.

const content = document.getElementById("content");
const pageHead = document.getElementById("quiz-head");
const dialog = document.getElementById("submit-dialog");
const params = new URLSearchParams(location.search);
const LETTERS = ["A", "B", "C", "D"];
const SECONDS_PER_QUESTION = 60;

const setup = {
  subject: quiz.subjects.find((subject) => subject.code === params.get("subject")) || quiz.subjects[0],
  unitNo: Number(params.get("unit")) || 2,
  count: 10,
};
let test = null;

const clock = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
const unitOf = () => setup.subject.units.find((unit) => unit.unitNo === setup.unitNo) || setup.subject.units[0];

// ---------------------------------------------------------------- Set up
function setupView() {
  const subjectSelect = h("select.select", { id: "quiz-subject" }, quiz.subjects.map((subject) => h("option", { value: subject.code, selected: subject === setup.subject }, `${subject.name} (${subject.code})`)));
  const unitSelect = h("select.select", { id: "quiz-unit" });
  const ready = h("span.row__meta.num", { "aria-live": "polite" });
  const time = h("strong.mono.num.time-pill");
  const problem = h("p.inset.inset--warning.inset--note", { hidden: true }, icon("alert"), "Not enough questions for this unit yet.");
  const start = h("button.button.button--primary.button--block.button--large", { type: "button" }, "Start test", icon("arrow-right"));

  function fillUnits() {
    fill(unitSelect, setup.subject.units.map((unit) => h("option", { value: unit.unitNo, selected: unit.unitNo === setup.unitNo }, `Unit ${unit.unitNo}: ${unit.title}`)));
  }
  function update() {
    const available = unitOf().questionsAvailable;
    ready.textContent = `${available} questions ready`;
    time.textContent = clock(setup.count * SECONDS_PER_QUESTION);
    const enough = available >= setup.count;
    problem.hidden = enough;
    start.disabled = !enough;
  }

  const counts = h(
    "div.count-pills",
    { role: "group", "aria-label": "Number of questions" },
    [5, 10, 15, 20].map((number) => {
      const pill = h("button.count-pill.num", { type: "button", "aria-pressed": String(number === setup.count) }, number);
      pill.addEventListener("click", () => {
        setup.count = number;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        update();
      });
      return pill;
    }),
  );
  subjectSelect.addEventListener("change", () => {
    setup.subject = quiz.subjects.find((subject) => subject.code === subjectSelect.value);
    setup.unitNo = setup.subject.units[0].unitNo;
    fillUnits();
    update();
  });
  unitSelect.addEventListener("change", () => {
    setup.unitNo = Number(unitSelect.value);
    update();
  });
  start.addEventListener("click", startTest);
  fillUnits();
  update();

  const form = h(
    "section.card.reveal",
    { "aria-labelledby": "setup-title" },
    h("div.line.line--start", h("span.badge-icon.badge-icon--round", icon("play")), h("div.stack.stack--tight", h("h2.card__title", { id: "setup-title" }, "Start a quick test"), h("p.row__meta", "Choose a unit and how many questions."))),
    h("div.field", h("label.field__label", { for: "quiz-subject" }, "Subject"), subjectSelect),
    h("div.field", h("div.line", h("label.field__label", { for: "quiz-unit" }, "Unit"), ready), unitSelect),
    h("div.field", h("span.field__label", "Number of questions"), counts),
    h("div.inset.inset--row", h("span.line.line--start", icon("timer"), h("span.stack.stack--tight", h("strong", "Time allowed"), h("span.row__meta", "1 minute per question. The test ends when the time runs out."))), time),
    problem,
    start,
  );

  const practice = h(
    "section.hero.reveal",
    { "data-tilt": true, "aria-label": "Your practice so far" },
    h("div.line", h("p.hero__eyebrow", "Your practice"), h("span.chip", `${progress.testsTaken} tests`)),
    h("div.stack.stack--tight", figureOf(progress.averagePercent, "%"), h("p.hero__sub", "Average score")),
    h(
      "div.stack",
      progress.subjects.slice(0, 3).map((subject) =>
        h("div.stack.stack--tight", h("div.line", h("span.small", subject.name), h("span.mono.num", subject.averagePercent + "%")), bar(subject.averagePercent)),
      ),
    ),
  );

  const recent = h(
    "section.card.reveal",
    cardHead("history", "Recent tests", "/student/quiz-history.html"),
    h(
      "ul.rows",
      quiz.attempts.slice(0, 3).map((attempt) => {
        const kind = scoreKind((attempt.score / attempt.total) * 100);
        return h(
          "li.row.attempt",
          h("span.badge-icon.badge-icon--round" + (kind ? ".badge-icon--" + kind : ".badge-icon--neutral"), icon(kind === "danger" ? "alert" : "check")),
          h("div.stack.stack--tight", h("span.row__title.clip", `${attempt.subjectName} · Unit ${attempt.unitNo}`), h("span.row__meta.num", formatDate(attempt.takenAt))),
          h("strong.num.attempt__score" + (kind ? ".attempt__score--" + kind : ""), `${attempt.score}/${attempt.total}`),
        );
      }),
    ),
  );

  return h("div.grid.grid--aside", form, h("div.stack.stack--loose", practice, recent));
}

function figureOf(value, suffix) {
  const figure = h("p.hero__figure");
  countUp(figure, value, { suffix });
  return figure;
}

// ---------------------------------------------------------------- Taking the test
function startTest() {
  const questions = quiz.questions.slice(0, Math.min(setup.count, quiz.questions.length));
  test = {
    subject: setup.subject,
    unit: unitOf(),
    questions,
    answers: questions.map(() => -1),
    current: 0,
    total: questions.length * SECONDS_PER_QUESTION,
    left: questions.length * SECONDS_PER_QUESTION,
    timer: null,
    warned: false,
  };
  pageHead.hidden = true;
  swap(content, () => {
    fill(content, testView());
    renderQuestion();
    tick();
    test.timer = setInterval(tick, 1000);
    window.scrollTo({ top: 0 });
  });
}

function tick() {
  const pill = document.getElementById("timer");
  if (!pill) {
    return;
  }
  document.getElementById("timer-text").textContent = clock(test.left);
  if (test.left <= 60 && !test.warned) {
    test.warned = true;
    pill.classList.add("timer--warning");
    document.getElementById("timer-alert").textContent = "One minute left.";
  }
  if (test.left <= 0) {
    finish();
    return;
  }
  test.left -= 1;
}

function testView() {
  return [
    h(
      "section.card.testbar",
      h("div.stack.stack--tight", h("p.eyebrow", `${test.subject.name} · Unit ${test.unit.unitNo}`), h("p.testbar__where", { id: "where", "aria-live": "polite" })),
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

function renderQuestion() {
  const index = test.current;
  const question = test.questions[index];
  const answered = test.answers.filter((answer) => answer >= 0).length;
  document.getElementById("where").textContent = `Question ${index + 1} of ${test.questions.length}`;
  document.getElementById("answered-text").textContent = `${answered} of ${test.questions.length} answered`;
  document.getElementById("progress-fill").style.setProperty("--value", String((answered / test.questions.length) * 100));

  const options = question.options.map((text, optionIndex) => {
    const chosen = test.answers[index] === optionIndex;
    return h(
      "button.option",
      { type: "button", "aria-pressed": String(chosen), onclick: () => answer(optionIndex) },
      h("span.option__letter", LETTERS[optionIndex]),
      h("span.option__text", text),
      h("span.option__check", icon("check")),
    );
  });
  const previous = h("button.button.button--quiet", { type: "button", disabled: index === 0, onclick: () => go(index - 1) }, icon("arrow-left"), "Previous");
  const last = index === test.questions.length - 1;
  const next = h("button.button.button--primary", { type: "button", onclick: () => (last ? askToSubmit() : go(index + 1)) }, last ? "Finish" : "Next", icon(last ? "check" : "arrow-right"));
  const clear = h("button.button.button--text", { type: "button", hidden: test.answers[index] < 0, onclick: () => answer(-1) }, "Clear answer");
  fill(
    document.getElementById("question"),
    h("p.eyebrow", `Question ${index + 1}`),
    h("h2.question__text", question.text),
    h("div.options", { role: "group", "aria-label": "Answers" }, options),
    h("div.question__actions", previous, clear, next),
  );

  fill(
    document.getElementById("qgrid"),
    test.questions.map((_, number) =>
      h(
        "button.qdot.num" + (test.answers[number] >= 0 ? ".qdot--answered" : "") + (number === index ? ".qdot--current" : ""),
        { type: "button", "aria-label": `Question ${number + 1}` + (test.answers[number] >= 0 ? ", answered" : ", not answered"), onclick: () => go(number) },
        number + 1,
      ),
    ),
  );
}

function answer(optionIndex) {
  test.answers[test.current] = optionIndex;
  renderQuestion();
}

function go(index) {
  test.current = Math.max(0, Math.min(test.questions.length - 1, index));
  const card = document.getElementById("question");
  swap(card, renderQuestion);
}

function askToSubmit() {
  const open = test.answers.map((answer, index) => (answer < 0 ? index + 1 : 0)).filter(Boolean);
  document.getElementById("submit-text").textContent = open.length
    ? `${open.length} question${open.length > 1 ? "s are" : " is"} unanswered (${open.join(", ")}). You cannot change your answers after submitting.`
    : "You have answered every question. You cannot change your answers after submitting.";
  document.getElementById("submit-done").textContent = `${test.questions.length - open.length} / ${test.questions.length}`;
  document.getElementById("submit-time").textContent = clock(Math.max(0, test.left));
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
  if (!test || test.timer === null || dialog.open || event.target.closest("select, input, textarea")) {
    return;
  }
  const letter = LETTERS.indexOf(event.key.toUpperCase());
  if (letter >= 0 && letter < test.questions[test.current].options.length) {
    answer(letter);
  } else if (event.key === "ArrowRight") {
    go(test.current + 1);
  } else if (event.key === "ArrowLeft") {
    go(test.current - 1);
  }
});

function finish() {
  clearInterval(test.timer);
  test.timer = null;
  if (dialog.open) {
    dialog.close();
  }
  showResult({
    title: `${test.subject.name} · Unit ${test.unit.unitNo}`,
    questions: test.questions,
    answers: test.answers,
    secondsTaken: test.total - Math.max(0, test.left),
  });
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

function stateOf(result, index) {
  if (result.answers[index] < 0) {
    return "open";
  }
  return result.answers[index] === result.questions[index].correct ? "right" : "wrong";
}

const STATES = {
  right: ["Correct", "success", "check"],
  wrong: ["Incorrect", "danger", "x"],
  open: ["Not answered", "", "minus"],
};

function resultView(result) {
  const states = result.questions.map((_, index) => stateOf(result, index));
  const countOf = (state) => states.filter((entry) => entry === state).length;
  const score = countOf("right");
  const percentage = Math.round((score / result.questions.length) * 100);
  const figure = h("p.hero__figure", h("span", { id: "score" }, "0"), h("small", ` / ${result.questions.length}`));
  countUp(figure.firstChild, score);
  let filter = "all";

  const list = h("div.stack.stack--loose", { id: "review" });
  function renderReview() {
    fill(
      list,
      stagger(
        result.questions
          .map((question, index) => ({ question, index, state: states[index] }))
          .filter((item) => filter === "all" || item.state === filter)
          .map(reviewCard),
      ),
    );
  }
  function reviewCard({ question, index, state }) {
    const [label, kind, iconName] = STATES[state];
    const chosen = result.answers[index];
    return h(
      "article.card.reveal",
      h("div.line", h("span.line.line--start", h("span.eyebrow.num", "Q" + (index + 1)), tag(label, kind, iconName))),
      h("h3.question__text.question__text--small", question.text),
      h(
        "div.tiles.tiles--answers",
        h("div.inset.answer" + (state === "wrong" ? ".answer--wrong" : state === "right" ? ".answer--right" : ""), h("p.eyebrow", "Your answer"), h("p.line", h("span", chosen < 0 ? "None selected" : question.options[chosen]), icon(state === "right" ? "check-circle" : state === "wrong" ? "x-circle" : "minus-circle"))),
        h("div.inset.answer.answer--right", h("p.eyebrow", "Correct answer"), h("p.line", h("strong", question.options[question.correct]), icon("check-circle"))),
      ),
      h("p.note", icon("info"), h("span", h("strong", "Explanation: "), question.explanation)),
    );
  }

  const filters = h(
    "div.pills",
    { role: "group", "aria-label": "Show" },
    [["all", "All", result.questions.length], ["right", "Correct", countOf("right")], ["wrong", "Incorrect", countOf("wrong")], ["open", "Not answered", countOf("open")]].map(([value, label, count]) => {
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
  return [
    h(
      "section.hero.hero--split.reveal",
      { "data-tilt": true, "aria-label": "Your score" },
      h(
        "div.hero__main",
        h("p.hero__eyebrow", "Test completed"),
        h("h2.hero__title.hero__title--small", result.title),
        figure,
        h(
          "ul.chips",
          h("li.chip.chip--good", icon("check"), "Correct ", h("strong", countOf("right"))),
          h("li.chip.chip--bad", icon("x"), "Incorrect ", h("strong", countOf("wrong"))),
          h("li.chip", icon("minus"), "Not answered ", h("strong", countOf("open"))),
          result.secondsTaken === null ? null : h("li.chip", icon("timer"), "Time ", h("strong", clock(result.secondsTaken))),
        ),
        h("div.line.line--start.line--wrap", h("a.button.button--primary", { href: "/student/quiz.html" }, icon("refresh"), "Take another test"), h("a.button.button--glass", { href: "/student/progress.html" }, icon("progress"), "See your progress")),
      ),
      h("div.hero__aside", ring(percentage, { size: 168, stroke: 14, label: [h("strong.ring__big", percentage + "%"), h("span.eyebrow.eyebrow--inverse", "Score")] })),
    ),
    h("div.section-head.reveal", h("div.section-head__text", h("h2", "Review your answers"), h("p.row__meta", "The correct answer and a short explanation for every question.")), filters),
    list,
  ];
}

// ---------------------------------------------------------------- Start
const attempt = quiz.attempts.find((entry) => String(entry.id) === params.get("attempt"));
if (attempt) {
  // Opened from the history page: show the result of a past test.
  fill(content, resultView({ title: `${attempt.subjectName} · Unit ${attempt.unitNo}`, questions: quiz.questions, answers: quiz.sampleAnswers, secondsTaken: null }));
  pageHead.hidden = true;
} else {
  fill(content, setupView());
}
reveal();
enableTilt();
