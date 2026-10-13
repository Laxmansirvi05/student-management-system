import { confirmAction, field, hidePageError, input, numberSelect, openForm, rowAction, semesterOptions } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: the timetable (FR-ADM-005). One section at a time, grouped by day. Two classes of the
// same section cannot overlap; the server refuses and says so.

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const content = document.getElementById("content");
const filters = { branch: "CSE", semester: "3", section: "A" };
let subjects = [];

const subjectOf = (id) => subjects.find((subject) => subject.id === id);

function entryForm(entry, day) {
  const choices = subjects.filter((subject) => subject.active).map((subject) => [subject.id, `${subject.code} · ${subject.name}`]);
  openForm({
    title: entry ? "Edit class" : "Add a class",
    fields: [
      field("Branch", input("branch", { value: entry?.branch || filters.branch, maxlength: 20 })),
      field("Semester", numberSelect("semester", semesterOptions(), entry?.semester || filters.semester || 1)),
      field("Section", input("section", { value: entry?.section || filters.section, maxlength: 5 })),
      field("Day", numberSelect("dayOfWeek", DAYS.map((name, index) => [index + 1, name]), entry?.dayOfWeek || day || 1)),
      field("Starts", input("startTime", { type: "time", value: entry?.startTime || "09:00" })),
      field("Ends", input("endTime", { type: "time", value: entry?.endTime || "10:00" })),
      field("Subject", numberSelect("subjectId", choices, entry?.subjectId || "")),
      field("Room (optional)", input("room", { value: entry?.room || "", maxlength: 30 })),
      field("Faculty (optional)", input("facultyName", { value: entry?.facultyName || "", maxlength: 120 })),
    ],
    wide: true,
    onSubmit: async (values) => {
      if (entry) {
        await api.put("/admin/timetable/" + entry.id, values);
      } else {
        await api.post("/admin/timetable", values);
      }
      toast("Saved");
      load();
    },
  });
}

async function remove(entry) {
  const subject = subjectOf(entry.subjectId);
  const yes = await confirmAction({ title: "Remove this class?", text: `${DAYS[entry.dayOfWeek - 1]} ${entry.startTime} – ${entry.endTime}, ${subject ? subject.name : "subject"}.`, confirmLabel: "Remove", danger: true });
  if (yes) {
    await api.delete("/admin/timetable/" + entry.id);
    toast("Removed");
    load();
  }
}

function dayCard(dayNumber, entries) {
  const add = rowAction("Add a class on " + DAYS[dayNumber - 1], "plus", () => entryForm(null, dayNumber));
  return h(
    "section.card.day-group",
    h("div.line", h("h2", DAYS[dayNumber - 1]), add),
    entries.length
      ? h(
          "ul.rows",
          entries.map((entry) => {
            const subject = subjectOf(entry.subjectId);
            return h(
              "li.row",
              h("div.row__top", h("span.mono.num", `${entry.startTime} – ${entry.endTime}`), h("span.row-actions", rowAction("Edit this class", "edit", () => entryForm(entry)), rowAction("Remove this class", "trash", () => remove(entry), "danger"))),
              h("span.row__title", subject ? `${subject.name} · ${subject.code}` : "Subject " + entry.subjectId),
              h("span.row__meta", [entry.room, entry.facultyName].filter(Boolean).join(" · ") || "No room or faculty set"),
            );
          }),
        )
      : h("p.muted.small", "No classes."),
  );
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      params.set(key, value);
    }
  }
  loadInto(list, () => api.get("/admin/timetable?" + params), (entries) => {
    if (!filters.branch || !filters.semester || !filters.section) {
      fill(list, h("div.card", empty("calendar", "Choose a section", "Enter the branch, semester and section to see its week.")));
      return;
    }
    fill(list, h("div.grid.grid--3", [1, 2, 3, 4, 5, 6].map((day) => dayCard(day, entries.filter((entry) => entry.dayOfWeek === day)))));
  });
}

function filterBar() {
  const branch = input("branch-filter", { value: filters.branch });
  branch.addEventListener("input", () => {
    filters.branch = branch.value.trim().toUpperCase();
    load();
  });
  const semester = numberSelect("semester-filter", semesterOptions(), filters.semester);
  semester.addEventListener("change", () => {
    filters.semester = semester.value;
    load();
  });
  const section = input("section-filter", { value: filters.section });
  section.addEventListener("input", () => {
    filters.section = section.value.trim().toUpperCase();
    load();
  });
  return h("section.card.toolbar-card", { "aria-label": "Section" }, h("div.filters-row", field("Branch", branch), field("Semester", semester), field("Section", section)));
}

loadInto(content, () => api.get("/admin/subjects"), (loaded) => {
  subjects = loaded;
  const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "Add class");
  add.addEventListener("click", () => entryForm(null));
  fill(document.getElementById("actions"), add);
  fill(content, filterBar(), h("div", { id: "list", "aria-live": "polite" }));
  load();
});
