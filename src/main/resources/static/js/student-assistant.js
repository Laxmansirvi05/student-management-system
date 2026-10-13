import { fill, h, icon, tag, toast } from "./dom.js";
import { reveal, ring, segmented, swap } from "./motion.js";
import { assistant, quiz } from "./sample-data.js";
import "./shell.js";

// The AI study assistant (FR-AI-001 to FR-AI-004): three small forms, not a chat. Every answer is
// labelled as AI-generated and is shown as plain text. In the finished feature the request goes
// to the server, which calls the AI provider; here a sample answer stands in for it.

const content = document.getElementById("content");
const LIMITS = { topic: 200, notes: 6000 };
let tool = "explain";
let remaining = assistant.remainingToday;

const subjectOptions = () => quiz.subjects.map((subject) => h("option", { value: subject.code }, `${subject.name} (${subject.code})`));

/** A text field with its label, a live character counter and a place for an error. */
function textField({ id, label, limit, area = false, placeholder = "" }) {
  const input = area
    ? h("textarea.textbox.textbox--area", { id, maxlength: limit, placeholder, "aria-describedby": id + "-error" })
    : h("input.textbox", { id, type: "text", maxlength: limit, placeholder, autocomplete: "off", "aria-describedby": id + "-error" });
  const counter = h("span.row__meta.num", `0 / ${limit}`);
  const error = h("p.field__error", { id: id + "-error", hidden: true });
  input.addEventListener("input", () => {
    counter.textContent = `${input.value.length} / ${limit}`;
    input.removeAttribute("aria-invalid");
    error.hidden = true;
  });
  const fail = (message) => {
    input.setAttribute("aria-invalid", "true");
    error.textContent = message;
    error.hidden = false;
    input.focus();
  };
  return { input, fail, element: h("div.field", h("div.line", h("label.field__label", { for: id }, label), counter), input, error) };
}

function choice(label, options, chosen) {
  let value = chosen;
  const group = h(
    "div.pills",
    { role: "group", "aria-label": label },
    options.map(([key, text]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(key === value) }, text);
      pill.addEventListener("click", () => {
        value = key;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
      });
      return pill;
    }),
  );
  return { element: h("div.field", h("span.field__label", label), group), value: () => value };
}

const FORMS = {
  explain() {
    const topic = textField({ id: "topic", label: "Topic", limit: LIMITS.topic, placeholder: "For example: stacks and queues" });
    return {
      title: "Explain a topic",
      button: "Explain",
      fields: [
        h("div.field", h("label.field__label", { for: "explain-subject" }, "Subject"), h("select.select", { id: "explain-subject" }, subjectOptions())),
        choice("Level", [["simple", "Simple"], ["detailed", "Detailed"]], "simple").element,
        topic.element,
      ],
      check: () => (topic.input.value.trim() ? true : (topic.fail("Enter a topic to explain."), false)),
      sample: assistant.explain,
    };
  },
  summarize() {
    const notes = textField({ id: "notes", label: "Your notes", limit: LIMITS.notes, area: true, placeholder: "Paste the text you want summarized" });
    return {
      title: "Summarize your notes",
      button: "Summarize",
      fields: [notes.element],
      check: () => (notes.input.value.trim().length >= 40 ? true : (notes.fail("Paste at least a few sentences to summarize."), false)),
      sample: assistant.summary,
    };
  },
  questions() {
    const unit = h("select.select", { id: "questions-unit" });
    const subject = h("select.select", { id: "questions-subject" }, subjectOptions());
    const fillUnits = () => fill(unit, quiz.subjects.find((entry) => entry.code === subject.value).units.map((entry) => h("option", { value: entry.unitNo }, `Unit ${entry.unitNo}: ${entry.title}`)));
    subject.addEventListener("change", fillUnits);
    fillUnits();
    return {
      title: "Generate practice questions",
      button: "Generate questions",
      fields: [
        h("div.field", h("label.field__label", { for: "questions-subject" }, "Subject"), subject),
        h("div.field", h("label.field__label", { for: "questions-unit" }, "Unit"), unit),
        choice("How many", [["5", "5 questions"], ["10", "10 questions"]], "5").element,
      ],
      check: () => true,
      sample: assistant.questions,
    };
  },
};

function quotaText() {
  return `${remaining} of ${assistant.dailyLimit} requests left today`;
}

function resultCard(sample) {
  const copy = h("button.button.button--quiet.button--small", { type: "button" }, icon("copy"), "Copy");
  const body = h(
    "div.answer-text",
    h("h2.answer-text__title", sample.title),
    sample.sections.map((section) =>
      h(
        "section.answer-text__part",
        h("h3", section.heading),
        (section.paragraphs || []).map((paragraph) => h("p", paragraph)),
        section.bullets ? h("ul", section.bullets.map((item) => h("li", item))) : null,
        section.numbered ? h("ol", section.numbered.map((item) => h("li", item))) : null,
      ),
    ),
  );
  copy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(body.innerText);
      toast("Copied");
    } catch {
      toast("Could not copy");
    }
  });
  return h(
    "section.card.reveal",
    { "aria-label": "Answer" },
    h("div.ai-label", tag("AI-generated", "purple", "sparkles"), h("span", "Check it against your course material."), h("span.row__meta", sample.basis)),
    body,
    h("div.card__foot", h("span", "Sample answer shown for the design."), copy),
  );
}

function waitingCard() {
  return h(
    "section.card",
    { role: "status" },
    h("div.line.line--start", h("span.spinner"), h("strong", "Working on it…"), h("span.row__meta", "This can take up to 30 seconds.")),
    [88, 96, 72, 90, 60].map((width) => h("div.skeleton", { style: { "--w": width + "%" } })),
  );
}

function errorCard(retry) {
  const again = h("button.button.button--secondary.button--small", { type: "button" }, icon("refresh"), "Retry");
  again.addEventListener("click", retry);
  return h("section.card", { role: "alert" }, h("div.line.line--start", h("span.badge-icon.badge-icon--round.badge-icon--danger", icon("alert")), h("div.stack.stack--tight", h("strong", "The assistant is unavailable right now"), h("span.row__meta", "Please try again."))), again);
}

function limitNote() {
  return h("p.inset.inset--warning.inset--note", icon("alert"), `You have used all ${assistant.dailyLimit} requests for today. They reset at midnight.`);
}

function formCard() {
  const form = FORMS[tool]();
  const quota = h("span.row__meta.num", { id: "quota" }, quotaText());
  const submit = h("button.button.button--primary", { type: "submit", disabled: remaining <= 0 }, icon("sparkles"), form.button);
  const element = h(
    "form.card.reveal",
    { novalidate: true, "aria-labelledby": "form-title" },
    h("h2.card__title", { id: "form-title" }, form.title),
    form.fields,
    remaining <= 0 ? limitNote() : null,
    h("div.line.line--wrap", submit, quota),
  );
  element.addEventListener("submit", (event) => {
    event.preventDefault();
    if (!form.check() || remaining <= 0) {
      return;
    }
    ask(form, submit);
  });
  return element;
}

/** Shows the waiting state, then the answer. The real page waits for the server here. */
function ask(form, submit) {
  const result = document.getElementById("result");
  if (!navigator.onLine) {
    fill(result, errorCard(() => ask(form, submit)));
    return;
  }
  submit.disabled = true;
  fill(result, waitingCard());
  result.scrollIntoView({ behavior: "smooth", block: "nearest" });
  setTimeout(() => {
    remaining -= 1;
    document.getElementById("quota").textContent = quotaText();
    renderSide();
    submit.disabled = remaining <= 0;
    swap(result, () => fill(result, resultCard(form.sample)));
  }, 1500);
}

function renderSide() {
  fill(
    document.getElementById("side"),
    h(
      "section.card",
      h("h2.card__title", icon("bolt"), "Requests today"),
      h("div.line", ring((remaining / assistant.dailyLimit) * 100, { size: 96, stroke: 9, label: h("strong.num", remaining) }), h("p.row__meta", `You can ask ${assistant.dailyLimit} times a day. The count resets at midnight.`)),
    ),
    h(
      "section.card.card--tinted",
      h("h2.card__title", icon("bulb"), "Good to know"),
      h(
        "ul.tips",
        h("li", "The assistant can be wrong. Check answers against your notes and textbook."),
        h("li", "It does not see your marks, attendance or any other record."),
        h("li", "It is a study aid, not a chat. Each request is answered on its own."),
      ),
    ),
  );
}

function render() {
  fill(
    content,
    h("p.strip.reveal", icon("shield"), h("span", h("strong", "Privacy: "), "Do not paste personal details. Text you send is processed by an AI provider.")),
    h("div.grid.grid--aside", h("div.stack.stack--loose", formCard(), h("div.stack.stack--loose", { id: "result", "aria-live": "polite" })), h("aside.stack.stack--loose.sticky.reveal", { id: "side" })),
  );
  renderSide();
  reveal();
}

segmented(document.getElementById("tool-tabs"), (value) => {
  tool = value;
  swap(content, render);
});
render();
