import { field, input, numberSelect, openForm, confirmAction, textarea } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { daysUntil, fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { reveal, segmented } from "./motion.js";
import { cardHead, dueText, empty, loadInto } from "./parts.js";
import "./shell.js";

// The planner (T7-02, FR-PLN-001 and FR-PLN-002): the student's study tasks and the countdown to
// their exams, their own and the college's. The server keeps the data and decides what is overdue
// or past; the number of days is counted here from the date with the same helper as the notice
// deadlines, so the countdown stays right after midnight without asking the server again.

const MINUTE = 60 * 1000;
const VIEWS = [
  ["pending", "To do"],
  ["overdue", "Overdue"],
  ["done", "Done"],
  ["all", "All"],
];
const state = { view: "pending", tasks: null, exams: null, subjects: [] };
let tabsReady = false;

const content = document.getElementById("content");
const examBox = h("div");
const taskBox = h("div");
const tabs = h(
  "div.segmented",
  { role: "tablist", "aria-label": "Which tasks" },
  VIEWS.map(([value, label]) =>
    h("button.segmented__option", { type: "button", role: "tab", "aria-selected": String(value === state.view), "data-value": value }, label, h("span.num.planner-count", { "data-count": value })),
  ),
);

/** "Today", "Tomorrow", "In 5 days", or for a past exam "2 days ago". */
function countdownText(isoDate) {
  const days = daysUntil(isoDate);
  if (days === 0) return "Today";
  if (days === 1) return "Tomorrow";
  if (days > 1) return `In ${days} days`;
  return days === -1 ? "Yesterday" : `${-days} days ago`;
}

/** Red within three days, amber within a week, plain after that. */
function countdownTag(isoDate) {
  const days = daysUntil(isoDate);
  const kind = days < 0 ? "" : days <= 3 ? "danger" : days <= 7 ? "warning" : "primary";
  return tag(countdownText(isoDate), kind, days < 0 ? "history" : "timer");
}

function subjectOptions() {
  return [["", "No subject"], ...state.subjects.map((subject) => [subject.id, `${subject.code} · ${subject.name}`])];
}

function today() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * MINUTE).toISOString().slice(0, 10);
}

// ---------------------------------------------------------------- Exams

function examRow(exam) {
  const college = exam.source === "COLLEGE";
  const title = college ? h("a.row__title.row__link", { href: "/student/notices.html?id=" + exam.noticeId }, exam.name) : h("span.row__title", exam.name);
  const actions = college
    ? null
    : h(
        "div.line",
        h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Edit ${exam.name}`, onclick: () => examForm(exam) }, icon("edit")),
        h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Delete ${exam.name}`, onclick: () => removeExam(exam) }, icon("trash")),
      );
  return h(
    "li.row" + (exam.past ? ".planner-row--past" : ""),
    h("div.row__top", countdownTag(exam.examDate), college ? tag("From your college", "purple", "notices") : exam.subject ? tag(exam.subject.code) : null, h("span.row__meta.num", formatDate(exam.examDate))),
    h("div.line", title, actions),
  );
}

function drawExams() {
  const { upcoming, past } = state.exams;
  const next = upcoming[0];
  fill(
    examBox,
    h(
      "section.card",
      cardHead("timer", "Exam countdown"),
      next
        ? h(
            "div.planner-next",
            h("p.eyebrow", "Next exam"),
            h("p.planner-next__days.num", countdownText(next.examDate)),
            h("p.row__title", next.name),
            h("p.row__meta", formatDate(next.examDate)),
          )
        : null,
      upcoming.length ? h("ul.rows", upcoming.map(examRow)) : empty("timer", "No exams coming up", "Add your exam dates to count down to them. Exam dates your college publishes appear here too."),
      past.length ? h("details.planner-past", h("summary.row__meta", `Past exams (${past.length})`), h("ul.rows", past.map(examRow))) : null,
    ),
  );
}

function examForm(exam = null) {
  openForm({
    title: exam ? "Edit exam" : "Add an exam",
    intro: "Only you can see the exams you add.",
    submitLabel: exam ? "Save" : "Add exam",
    fields: [
      field("Exam", input("name", { maxlength: 120, value: exam?.name ?? "", required: true, placeholder: "For example: Data Structures mid-term" })),
      field("Date", input("examDate", { type: "date", value: exam?.examDate ?? "", min: exam?.past ? null : today(), required: true })),
      field("Subject", numberSelect("subjectId", subjectOptions(), exam?.subject?.id ?? ""), "Optional."),
    ],
    onSubmit: async (values) => {
      if (exam) {
        await api.put(`/planner/exams/${exam.id}`, values);
        toast("Exam saved");
      } else {
        await api.post("/planner/exams", values);
        toast("Exam added");
      }
      await loadExams();
    },
  });
}

async function removeExam(exam) {
  const sure = await confirmAction({ title: "Delete this exam?", text: `“${exam.name}” is removed from your countdown.`, confirmLabel: "Delete", danger: true });
  if (!sure) return;
  try {
    await api.delete(`/planner/exams/${exam.id}`);
    toast("Exam deleted");
    await loadExams();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

// ---------------------------------------------------------------- Tasks

function taskRow(task) {
  const done = task.status === "DONE";
  const toggle = h(
    "button.icon-button.icon-button--bare.planner-check",
    { type: "button", "aria-pressed": String(done), "aria-label": done ? `Mark “${task.title}” as not done` : `Mark “${task.title}” as done`, onclick: () => setDone(task, !done) },
    icon(done ? "check-circle" : "minus-circle"),
  );
  const due = task.dueDate
    ? h("p.row__meta" + (task.overdue ? ".planner-overdue" : !done && daysUntil(task.dueDate) <= 2 ? ".row__meta--warning" : ""), done ? "Due " + formatDate(task.dueDate) : dueText(task.dueDate))
    : null;
  return h(
    "li.row" + (done ? ".planner-row--done" : ""),
    h("div.row__top", task.overdue ? tag("Overdue", "danger", "alert") : null, task.subject ? tag(task.subject.code) : null, task.dueDate ? h("span.row__meta.num", formatDate(task.dueDate)) : null),
    h(
      "div.line.planner-task",
      toggle,
      h("div.stack.stack--tight.planner-task__text", h("span.row__title", task.title), task.notes ? h("p.row__meta.planner-notes", task.notes) : null, due),
      h(
        "div.line",
        h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Edit ${task.title}`, onclick: () => taskForm(task) }, icon("edit")),
        h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Delete ${task.title}`, onclick: () => removeTask(task) }, icon("trash")),
      ),
    ),
  );
}

const EMPTY_TASKS = {
  pending: ["check-circle", "Nothing to do", "Add a task to plan your study."],
  overdue: ["check-circle", "Nothing overdue", "Every task with a past due date is done."],
  done: ["check", "No finished tasks yet", "Tasks you mark as done appear here."],
  all: ["check-circle", "No tasks yet", "Add a task to plan your study."],
};

function drawTasks() {
  const { tasks, counts } = state.tasks;
  for (const badge of tabs.querySelectorAll("[data-count]")) {
    const value = badge.dataset.count === "all" ? counts.pending + counts.done : counts[badge.dataset.count];
    badge.textContent = value ? ` ${value}` : "";
  }
  const [iconName, title, text] = EMPTY_TASKS[state.view];
  fill(
    taskBox,
    h(
      "section.card",
      cardHead("check-circle", "Study tasks"),
      tabs,
      tasks.length ? h("ul.rows", tasks.map(taskRow)) : empty(iconName, title, text),
    ),
  );
  // The tab highlight is measured from the tabs, so they must be on the page first; after that the
  // counts may change their widths, and segmented() re-measures on a resize.
  if (!tabsReady) {
    tabsReady = true;
    segmented(tabs, (value) => {
      state.view = value;
      loadTasks();
    });
  } else {
    window.dispatchEvent(new Event("resize"));
  }
}

function taskForm(task = null) {
  openForm({
    title: task ? "Edit task" : "Add a task",
    intro: "Only you can see your tasks.",
    submitLabel: task ? "Save" : "Add task",
    fields: [
      field("Task", input("title", { maxlength: 200, value: task?.title ?? "", required: true, placeholder: "For example: Revise unit 3 of DBMS" })),
      field("Notes", textarea("notes", { maxlength: 1000, rows: 3 }), "Optional, up to 1,000 characters."),
      field("Due date", input("dueDate", { type: "date", value: task?.dueDate ?? "", min: task?.overdue ? null : today() }), "Optional."),
      field("Subject", numberSelect("subjectId", subjectOptions(), task?.subject?.id ?? ""), "Optional."),
    ],
    onSubmit: async (values) => {
      if (task) {
        await api.put(`/planner/tasks/${task.id}`, values);
        toast("Task saved");
      } else {
        await api.post("/planner/tasks", values);
        toast("Task added");
      }
      await loadTasks();
    },
  });
  if (task?.notes) {
    document.getElementById("f-notes").value = task.notes;
  }
}

async function setDone(task, done) {
  try {
    await api.post(`/planner/tasks/${task.id}/${done ? "complete" : "reopen"}`);
    toast(done ? "Marked as done" : "Marked as not done");
    await loadTasks();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

async function removeTask(task) {
  const sure = await confirmAction({ title: "Delete this task?", text: `“${task.title}” is deleted for good.`, confirmLabel: "Delete", danger: true });
  if (!sure) return;
  try {
    await api.delete(`/planner/tasks/${task.id}`);
    toast("Task deleted");
    await loadTasks();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

// ---------------------------------------------------------------- Loading

async function loadTasks() {
  await loadInto(taskBox, () => api.get(`/planner/tasks?view=${state.view}`), (data) => {
    state.tasks = data;
    drawTasks();
  });
}

async function loadExams() {
  await loadInto(examBox, () => api.get("/planner/exams"), (data) => {
    state.exams = data;
    drawExams();
  });
}

const addTask = h("button.button.button--primary", { type: "button", onclick: () => taskForm() }, icon("plus"), "Add task");
const addExam = h("button.button.button--secondary", { type: "button", onclick: () => examForm() }, icon("plus"), "Add exam");
fill(document.getElementById("actions"), addExam, addTask);
fill(content, h("div.planner-grid", examBox, taskBox));

api
  .get("/subjects")
  .then((subjects) => (state.subjects = subjects))
  .catch(() => {});
Promise.all([loadExams(), loadTasks()]).then(() => reveal());

// The countdown and the due dates move on by themselves: redraw them every minute and when the page
// comes back into view, from what is already loaded. No request is sent.
function redraw() {
  if (state.exams) drawExams();
  if (state.tasks) drawTasks();
}
setInterval(redraw, MINUTE);
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") redraw();
});
