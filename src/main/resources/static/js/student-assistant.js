import { api, messageFor } from "./api.js";
import { fill, h, icon, tag, toast } from "./dom.js";
import { reveal, ring, segmented, swap } from "./motion.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// The AI study assistant (FR-AI-001 to FR-AI-004, FR-AI-006): four small forms, not a chat. Each request
// stands alone and nothing is remembered. The server calls the AI provider; the browser never
// does, and never holds a key. Every answer is labelled as AI-generated and is put on the page as
// plain text, so nothing the AI writes can run as markup.

const content = document.getElementById("content");
const PREFILL = "assistant-prefill";
const LIMITS = { topic: 200, context: 2000, text: 12000, sourceText: 8000, processText: 3000 };
const BASIS = {
  GENERAL_KNOWLEDGE: "Based on general knowledge",
  PROVIDED_TEXT: "Based on the text you provided",
  MIXED: "Based on the text you provided and general knowledge",
};
const state = { tool: "explain", usage: null, subjects: [], prefill: readPrefill() };

/** A topic handed over by the quiz result page ("Ask AI to explain"). It is used once. */
function readPrefill() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(PREFILL));
    sessionStorage.removeItem(PREFILL);
    return saved && typeof saved.topic === "string" ? saved : null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------- Form pieces
/** A text field with its label, a live character counter and a place for an error. */
function textField({ name, label, limit, area = false, placeholder = "", value = "", hint = "" }) {
  const id = "f-" + name;
  const input = area
    ? h("textarea.textbox.textbox--area", { id, name, maxlength: limit, placeholder, "aria-describedby": id + "-error" })
    : h("input.textbox", { id, name, type: "text", maxlength: limit, placeholder, autocomplete: "off", "aria-describedby": id + "-error" });
  input.value = value;
  const counter = h("span.row__meta.num", `${value.length} / ${limit.toLocaleString("en-IN")}`);
  const error = h("p.field__error", { id: id + "-error", hidden: true });
  input.addEventListener("input", () => {
    counter.textContent = `${input.value.length.toLocaleString("en-IN")} / ${limit.toLocaleString("en-IN")}`;
    input.removeAttribute("aria-invalid");
    error.hidden = true;
  });
  return {
    name,
    input,
    value: () => input.value.trim(),
    fail(message) {
      input.setAttribute("aria-invalid", "true");
      error.textContent = message;
      error.hidden = false;
    },
    element: h("div.field", h("div.line", h("label.field__label", { for: id }, label), counter), input, hint ? h("p.field__help", hint) : null, error),
  };
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

/** Subject and unit, both optional. The unit list follows the subject. */
function subjectAndUnit(withUnit, chosenSubject = "", chosenUnit = "") {
  const subject = h(
    "select.select",
    { id: "f-subjectId" },
    h("option", { value: "" }, "No particular subject"),
    state.subjects.map((entry) => h("option", { value: entry.id, selected: String(entry.id) === String(chosenSubject) }, `${entry.name} (${entry.code})`)),
  );
  const unit = h("select.select", { id: "f-unitId", disabled: true }, h("option", { value: "" }, "Whole subject"));
  async function fillUnits(chosen = "") {
    fill(unit, h("option", { value: "" }, "Whole subject"));
    unit.disabled = !subject.value;
    if (!subject.value) {
      return;
    }
    try {
      const units = await api.get(`/subjects/${subject.value}/units`);
      fill(unit, h("option", { value: "" }, "Whole subject"), units.map((entry) => h("option", { value: entry.id, selected: String(entry.id) === String(chosen) }, `Unit ${entry.unitNo}: ${entry.title}`)));
    } catch {
      unit.disabled = true;
    }
  }
  subject.addEventListener("change", () => fillUnits());
  fillUnits(chosenUnit);
  return {
    elements: [
      h("div.field", h("label.field__label", { for: "f-subjectId" }, "Subject (optional)"), subject),
      withUnit ? h("div.field", h("label.field__label", { for: "f-unitId" }, "Unit (optional)"), unit) : null,
    ],
    subjectId: () => (subject.value ? Number(subject.value) : null),
    unitId: () => (withUnit && unit.value ? Number(unit.value) : null),
  };
}

// ---------------------------------------------------------------- The four tools
const TOOLS = {
  explain() {
    const prefill = state.prefill;
    state.prefill = null;
    const topic = textField({ name: "topic", label: "Topic", limit: LIMITS.topic, placeholder: "For example: stacks and queues", value: prefill?.topic || "" });
    const context = textField({ name: "context", label: "Your own material (optional)", limit: LIMITS.context, area: true, placeholder: "Paste a paragraph from your notes if the answer should follow it", hint: "When you paste material, the answer is based on it." });
    const place = subjectAndUnit(true, prefill?.subjectId, prefill?.unitId);
    const format = choice("Answer format", [["SIMPLE", "Simple"], ["FIVE_MARK", "5-mark answer"], ["TEN_MARK", "10-mark answer"]], "SIMPLE");
    return {
      title: "Explain a topic",
      button: "Explain",
      path: "/ai/explain",
      fields: [topic, context],
      elements: [topic.element, format.element, ...place.elements, context.element],
      body: () => ({ topic: topic.value(), subjectId: place.subjectId(), unitId: place.unitId(), answerFormat: format.value(), context: context.value() || null }),
      check: () => (topic.value().length >= 3 ? true : (topic.fail("Enter a topic of at least 3 characters."), false)),
      render: explanationBody,
    };
  },
  summarize() {
    const text = textField({ name: "text", label: "Your notes", limit: LIMITS.text, area: true, placeholder: "Paste the text you want summarized", hint: "Between 200 and 12,000 characters." });
    const place = subjectAndUnit(false);
    const style = choice("Style", [["SHORT", "Short summary"], ["REVISION_NOTES", "Revision notes"]], "SHORT");
    return {
      title: "Summarize your notes",
      button: "Summarize",
      path: "/ai/summarize",
      fields: [text],
      elements: [text.element, style.element, ...place.elements],
      body: () => ({ text: text.value(), style: style.value(), subjectId: place.subjectId() }),
      check: () => (text.value().length >= 200 ? true : (text.fail("Paste at least 200 characters to summarize."), false)),
      render: summaryBody,
    };
  },
  questions() {
    const topic = textField({ name: "topic", label: "Topic", limit: LIMITS.topic, placeholder: "For example: TCP and UDP" });
    const sourceText = textField({ name: "sourceText", label: "Or your own text", limit: LIMITS.sourceText, area: true, placeholder: "Paste text to get questions that it answers", hint: "Give a topic or paste text, not both. Text needs 200 to 8,000 characters." });
    const place = subjectAndUnit(true);
    const type = choice("Question type", [["MCQ", "Multiple choice"], ["SHORT", "Short answer"], ["LONG", "Long answer"]], "MCQ");
    const count = choice("How many", [["3", "3"], ["5", "5"], ["10", "10"]], "5");
    return {
      title: "Generate practice questions",
      button: "Generate questions",
      path: "/ai/generate-quiz",
      fields: [topic, sourceText],
      elements: [topic.element, sourceText.element, type.element, count.element, ...place.elements],
      body: () => ({ topic: topic.value() || null, sourceText: sourceText.value() || null, subjectId: place.subjectId(), unitId: place.unitId(), questionType: type.value(), count: Number(count.value()) }),
      check() {
        if (Boolean(topic.value()) === Boolean(sourceText.value())) {
          topic.fail("Give a topic or paste text, but not both.");
          return false;
        }
        return true;
      },
      render: questionsBody,
    };
  },
  flowchart() {
    const topic = textField({ name: "topic", label: "Topic", limit: LIMITS.topic, placeholder: "For example: binary search" });
    const processText = textField({ name: "processText", label: "Or describe a process", limit: LIMITS.processText, area: true, placeholder: "Describe the steps in your own words", hint: "Give a topic or describe a process, not both. A description needs 20 to 3,000 characters." });
    const place = subjectAndUnit(false);
    return {
      title: "Make a flowchart",
      button: "Make flowchart",
      path: "/ai/flowchart",
      fields: [topic, processText],
      elements: [topic.element, processText.element, ...place.elements],
      body: () => ({ topic: topic.value() || null, processText: processText.value() || null, subjectId: place.subjectId() }),
      check() {
        if (Boolean(topic.value()) === Boolean(processText.value())) {
          topic.fail("Give a topic or describe a process, but not both.");
          return false;
        }
        return true;
      },
      render: flowchartBody,
    };
  },
};

// ---------------------------------------------------------------- Results, as plain text
function paragraphs(text) {
  return text.split(/\n\s*\n|\n/).filter((part) => part.trim()).map((part) => h("p", part));
}

function part(heading, ...children) {
  return h("section.answer-text__part", h("h3", heading), children);
}

function explanationBody(answer) {
  return h(
    "div.answer-text",
    h("h2.answer-text__title", answer.topic),
    part("Explanation", paragraphs(answer.explanation)),
    part("Key points", h("ul", answer.keyPoints.map((point) => h("li", point)))),
    answer.example ? part("Example", paragraphs(answer.example)) : null,
  );
}

function summaryBody(answer) {
  return h(
    "div.answer-text",
    h("h2.answer-text__title", "Summary"),
    part("Summary", paragraphs(answer.summary)),
    answer.keyPoints.length ? part("Key points", h("ul", answer.keyPoints.map((point) => h("li", point)))) : null,
    answer.definitions.length ? part("Terms the text defines", h("ul", answer.definitions.map((entry) => h("li", h("strong", entry.term + ": "), entry.meaning)))) : null,
  );
}

function questionsBody(answer) {
  const short = answer.returned < answer.requested ? h("p.row__meta", `${answer.returned} of the ${answer.requested} questions passed the checks and are shown.`) : null;
  return h(
    "div.answer-text",
    h("h2.answer-text__title", "Practice questions"),
    short,
    answer.questions.map((question, index) =>
      part(
        "Question " + (index + 1),
        h("p", question.question),
        question.options ? h("ol.options-list", question.options.map((option) => h("li", option))) : null,
        h(
          "details.reveal-answer",
          h("summary", "Show the answer"),
          h("p", h("strong", "Answer: "), (question.correctOption ? question.correctOption + ". " : "") + question.answer),
          h("p", h("strong", "Why: "), question.explanation),
        ),
      ),
    ),
  );
}

/**
 * The chart drawn with plain boxes, in reading order. The server has already checked the graph
 * and numbered the nodes; where a step does not simply lead to the next one, its arrows are
 * written out ("Yes → step 5").
 */
function flowchartBody(answer) {
  const number = new Map(answer.nodes.map((node, index) => [node.id, index + 1]));
  const label = new Map(answer.nodes.map((node) => [node.id, node.label]));
  const items = answer.nodes.map((node, index) => {
    const next = answer.edges.filter((edge) => edge.from === node.id);
    const straight = next.length === 1 && !next[0].label && number.get(next[0].to) === index + 2;
    const links = straight || !next.length
      ? null
      : h("ul.flowchart__links", next.map((edge) => h("li", h("strong", (edge.label || "Then") + " → "), `step ${number.get(edge.to)}: ${label.get(edge.to)}`)));
    return h(
      "li.flowchart__item" + (straight ? ".flowchart__item--straight" : ""),
      h("div.flowchart__node.flowchart__node--" + node.type.toLowerCase(), h("span.flowchart__num.num", number.get(node.id)), h("span", node.label)),
      links,
    );
  });
  const copyMermaid = h("button.button.button--quiet.button--small", { type: "button" }, icon("copy"), "Copy Mermaid text");
  copyMermaid.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(answer.mermaid);
      toast("Mermaid text copied");
    } catch {
      toast("Could not copy");
    }
  });
  return h(
    "div.answer-text",
    h("h2.answer-text__title", answer.title),
    h("ol.flowchart", { "aria-label": "Flowchart" }, items),
    part("As numbered steps", h("ol", answer.steps.map((step) => h("li", step)))),
    h(
      "details.reveal-answer",
      h("summary", "Mermaid text"),
      h("p.row__meta", "Paste it into any Mermaid editor to draw the chart there."),
      h("pre.flowchart__code", answer.mermaid),
      copyMermaid,
    ),
  );
}

function resultCard(tool, answer) {
  const body = tool.render(answer);
  const copy = h("button.button.button--quiet.button--small", { type: "button" }, icon("copy"), "Copy");
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
    // The notice is the server's own fixed text, not something the AI wrote.
    h("div.ai-label", tag("AI-generated", "purple", "sparkles"), h("span", answer.notice.replace(/^AI-generated\.?\s*/, "")), h("span.row__meta", BASIS[answer.basis] || "")),
    body,
    h("div.card__foot", h("span", "Not saved. Copy it if you want to keep it."), copy),
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

/** What to say for each way a request can fail, and whether trying again can help. */
function failureOf(failure) {
  switch (failure.code) {
    case "AI_REQUEST_DECLINED":
      return { title: "The assistant did not answer this", text: failure.message, retry: false, kind: "warning", iconName: "info" };
    case "AI_QUOTA_EXCEEDED":
      return { title: "You have used today's AI requests", text: "Try again tomorrow.", retry: false, kind: "warning", iconName: "clock" };
    case "RATE_LIMITED":
      return { title: "Please wait a moment", text: "You can send a few requests each minute.", retry: true, kind: "warning", iconName: "clock" };
    case "AI_UNAVAILABLE":
      // The server says which: switched off or a provider failure (Retry), or everyone's limit.
      return { title: "The assistant is unavailable right now", text: failure.message.includes("everyone") ? failure.message : "Please try again.", retry: !failure.message.includes("everyone"), kind: "danger", iconName: "alert" };
    case "AI_TIMEOUT":
    case "AI_INVALID_RESPONSE":
      return { title: "The assistant is unavailable right now", text: "Please try again.", retry: true, kind: "danger", iconName: "alert" };
    default:
      return { title: "This did not work", text: messageFor(failure), retry: true, kind: "danger", iconName: "alert" };
  }
}

function failureCard(failure, retry) {
  const info = failureOf(failure);
  const again = info.retry ? h("button.button.button--secondary.button--small", { type: "button" }, icon("refresh"), "Retry") : null;
  again?.addEventListener("click", retry);
  return h(
    "section.card",
    { role: "alert" },
    h("div.line.line--start", h("span.badge-icon.badge-icon--round.badge-icon--" + info.kind, icon(info.iconName)), h("div.stack.stack--tight", h("strong", info.title), h("span.row__meta", info.text))),
    again,
  );
}

// ---------------------------------------------------------------- The form
function quotaText() {
  const usage = state.usage;
  return `${usage.remainingToday} of ${usage.dailyLimit} requests left today`;
}

function blockedNote() {
  if (!state.usage.enabled) {
    return h("p.inset.inset--warning.inset--note", icon("alert"), "The assistant is unavailable right now. Please try again later.");
  }
  if (state.usage.remainingToday <= 0) {
    return h("p.inset.inset--warning.inset--note", icon("clock"), `You have used all ${state.usage.dailyLimit} requests for today. They reset at midnight.`);
  }
  return null;
}

function formCard() {
  const tool = TOOLS[state.tool]();
  const blocked = blockedNote();
  const quota = h("span.row__meta.num", { id: "quota" }, quotaText());
  const submit = h("button.button.button--primary", { type: "submit", disabled: Boolean(blocked) }, icon("sparkles"), tool.button);
  const fieldset = h("fieldset.form-lock", tool.elements);
  const form = h("form.card.reveal", { novalidate: true, "aria-labelledby": "form-title" }, h("h2.card__title", { id: "form-title" }, tool.title), fieldset, blocked, h("div.line.line--wrap", submit, quota));

  async function ask() {
    const result = document.getElementById("result");
    // While the request runs the whole form is locked, so it cannot be sent twice.
    fieldset.disabled = true;
    submit.disabled = true;
    fill(result, waitingCard());
    result.scrollIntoView({ behavior: "smooth", block: "nearest" });
    try {
      const answer = await api.post(tool.path, tool.body());
      swap(result, () => {
        fill(result, resultCard(tool, answer));
        reveal();
      });
    } catch (failure) {
      if (failure.fieldErrors?.length) {
        fill(result);
        for (const { field, message } of failure.fieldErrors) {
          (tool.fields.find((entry) => entry.name === field) || tool.fields[0]).fail(message);
        }
        form.querySelector("[aria-invalid]")?.focus();
      } else {
        fill(result, failureCard(failure, ask));
      }
    } finally {
      fieldset.disabled = false;
      await refreshUsage();
      submit.disabled = !state.usage.enabled || state.usage.remainingToday <= 0;
      quota.textContent = quotaText();
    }
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (!submit.disabled && tool.check()) {
      ask();
    } else {
      form.querySelector("[aria-invalid]")?.focus();
    }
  });
  return form;
}

async function refreshUsage() {
  try {
    state.usage = await api.get("/ai/usage/me");
  } catch {
    // The last known numbers stay on the page.
  }
  renderSide();
}

function renderSide() {
  const usage = state.usage;
  fill(
    document.getElementById("side"),
    h(
      "section.card",
      h("h2.card__title", icon("bolt"), "Requests today"),
      h("div.line", ring((usage.remainingToday / Math.max(1, usage.dailyLimit)) * 100, { size: 96, stroke: 9, label: h("strong.num", usage.remainingToday) }), h("p.row__meta", `You can ask ${usage.dailyLimit} times a day. The count resets at midnight.`)),
    ),
    h(
      "section.card.card--tinted",
      h("h2.card__title", icon("bulb"), "Good to know"),
      h(
        "ul.tips",
        h("li", "The assistant can be wrong. Check answers against your notes and textbook."),
        h("li", "It does not see your marks, attendance or any other record."),
        h("li", "It is a study aid, not a chat. Each request is answered on its own, and nothing is saved."),
        h("li", "It cannot tell you what will be in an exam."),
      ),
    ),
  );
}

function render() {
  fill(
    content,
    h("p.strip.reveal", icon("shield"), h("span", h("strong", "Privacy: "), "Do not paste personal information. Text you submit is sent to an AI service.")),
    state.usage.demo
      ? h("p.inset.inset--warning.inset--note.reveal", icon("info"), h("span", h("strong", "Demo mode: "), "no AI provider is connected yet, so the answers below are placeholders. The limits, labels and messages already work as they will."))
      : null,
    h("div.grid.grid--aside", h("div.stack.stack--loose", formCard(), h("div.stack.stack--loose", { id: "result", "aria-live": "polite" })), h("aside.stack.stack--loose.sticky.reveal", { id: "side" })),
  );
  renderSide();
  reveal();
}

segmented(document.getElementById("tool-tabs"), (value) => {
  state.tool = value;
  swap(content, render);
});

loadInto(content, () => Promise.all([api.get("/ai/usage/me"), api.get("/subjects")]), ([usage, subjects]) => {
  state.usage = usage;
  state.subjects = subjects;
  // The page opens on Explain, which is also where a question handed over by a quiz result goes.
  render();
});
