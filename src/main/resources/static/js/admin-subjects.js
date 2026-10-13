import { checkbox, confirmAction, dataTable, field, hidePageError, input, numberSelect, openForm, rowAction, semesterOptions } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, h, icon, tag, toast } from "./dom.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: subjects and their units (FR-ADM-005). A subject in use is deactivated, never deleted.

const content = document.getElementById("content");
const filters = { branch: "", semester: "" };

function subjectForm(subject) {
  const editing = Boolean(subject);
  openForm({
    title: editing ? "Edit " + subject.code : "Add a subject",
    fields: [
      field("Code", input("code", { value: subject?.code || "", maxlength: 20 }), "For example CS301."),
      field("Name", input("name", { value: subject?.name || "", maxlength: 150 })),
      field("Branch", input("branch", { value: subject?.branch || filters.branch, maxlength: 20 })),
      field("Semester", numberSelect("semester", semesterOptions(), subject?.semester || filters.semester || 1)),
      field("Credits", input("credits", { type: "number", value: subject?.credits ?? 4, min: 0, max: 10 })),
      field("Status", checkbox("active", "Active: students can see it", subject ? subject.active : true)),
    ],
    wide: true,
    onSubmit: async (values) => {
      if (editing) {
        await api.put("/admin/subjects/" + subject.id, values);
      } else {
        await api.post("/admin/subjects", values);
      }
      toast("Saved");
      load();
    },
  });
}

/** The units of a subject, in a dialog: the list, a way to rename or remove each, and one to add. */
function units(subject) {
  const holder = h("div.stack.stack--loose");
  const dialog = h("dialog.dialog.dialog--wide", { "aria-labelledby": "units-title" });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog || event.target.closest("[data-close]")) {
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => dialog.remove());

  function unitForm(unit, nextNumber) {
    openForm({
      title: unit ? "Edit unit " + unit.unitNo : "Add a unit",
      fields: [
        field("Unit number", input("unitNo", { type: "number", value: unit?.unitNo ?? nextNumber, min: 1, max: 20 })),
        field("Title", input("title", { value: unit?.title || "", maxlength: 200 })),
      ],
      onSubmit: async (values) => {
        if (unit) {
          await api.put("/admin/units/" + unit.id, values);
        } else {
          await api.post(`/admin/subjects/${subject.id}/units`, values);
        }
        refresh();
      },
    });
  }

  async function remove(unit) {
    const yes = await confirmAction({ title: `Remove unit ${unit.unitNo}?`, text: "A unit that resources or questions use cannot be removed.", confirmLabel: "Remove", danger: true });
    if (yes) {
      try {
        await api.delete("/admin/units/" + unit.id);
        refresh();
      } catch (failure) {
        toast(failure.message);
      }
    }
  }

  function refresh() {
    loadInto(holder, () => api.get(`/subjects/${subject.id}/units`), (list) => {
      const add = h("button.button.button--primary.button--small", { type: "button" }, icon("plus"), "Add unit");
      add.addEventListener("click", () => unitForm(null, list.length + 1));
      fill(
        holder,
        list.length
          ? h(
              "ul.unit-list",
              list.map((unit) =>
                h(
                  "li",
                  h("span", h("strong.num", `Unit ${unit.unitNo}: `), unit.title),
                  h("span.row-actions", rowAction("Edit unit " + unit.unitNo, "edit", () => unitForm(unit)), rowAction("Remove unit " + unit.unitNo, "trash", () => remove(unit), "danger")),
                ),
              ),
            )
          : h("p.muted", "This subject has no units yet."),
        h("div.line", add, h("button.button.button--quiet.button--small", { type: "button", "data-close": true }, "Close")),
      );
    });
  }

  fill(dialog, h("div.stack.stack--loose", h("div.stack.stack--tight", h("h2", { id: "units-title" }, `Units of ${subject.code}`), h("p.muted", subject.name)), holder));
  document.body.append(dialog);
  dialog.showModal();
  if (subject.active) {
    refresh();
  } else {
    fill(holder, h("p.muted", "Activate the subject to manage its units."), h("button.button.button--quiet.button--small", { type: "button", "data-close": true }, "Close"));
  }
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams();
  if (filters.branch) {
    params.set("branch", filters.branch);
  }
  if (filters.semester) {
    params.set("semester", filters.semester);
  }
  loadInto(list, () => api.get("/admin/subjects?" + params), (subjects) => {
    fill(
      list,
      h("p.row__meta.num", subjects.length === 1 ? "1 subject" : subjects.length + " subjects"),
      dataTable(
        [
          { label: "Code", cell: (subject) => h("strong.mono", subject.code) },
          { label: "Name", cell: (subject) => subject.name },
          { label: "Branch", cell: (subject) => subject.branch },
          { label: "Semester", cell: (subject) => subject.semester },
          { label: "Credits", cell: (subject) => subject.credits },
          { label: "Status", cell: (subject) => (subject.active ? tag("Active", "success") : tag("Inactive")) },
          {
            label: "Actions",
            align: "end",
            cell: (subject) =>
              h("span.row-actions", rowAction("Units of " + subject.code, "list", () => units(subject)), rowAction("Edit " + subject.code, "edit", () => subjectForm(subject))),
          },
        ],
        subjects,
        "No subjects yet. Add the first one with the button above.",
      ),
    );
  });
}

function filterBar() {
  const branch = input("branch-filter", { placeholder: "Any" });
  branch.addEventListener("input", () => {
    filters.branch = branch.value.trim().toUpperCase();
    load();
  });
  const semester = numberSelect("semester-filter", semesterOptions("Any semester"));
  semester.addEventListener("change", () => {
    filters.semester = semester.value;
    load();
  });
  return h("section.card.toolbar-card", { "aria-label": "Filters" }, h("div.filters-row", field("Branch", branch), field("Semester", semester)));
}

const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "Add subject");
add.addEventListener("click", () => subjectForm(null));
fill(document.getElementById("actions"), add);
fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Subjects" }, h("div.skeleton")));
load();

