import { confirmAction, dataTable, field, hidePageError, input, numberSelect, openForm, pager, rowAction, select, textarea } from "./admin-kit.js";
import { api, ApiError, messageFor } from "./api.js";
import { fill, formatDate, formatSize, h, icon, tag, toast } from "./dom.js";
import { empty, examText, examTypeLabels, loadInto, resourceTypes, typeTag } from "./parts.js";
import "./shell.js";

// Admin: learning resources (FR-ADM-007). A PDF is uploaded with its details and is published at
// once. Its details can be edited later, it can be hidden from students, and it can be deleted.
// The server checks every file again; the checks here only save a wasted upload.

const MAX_BYTES = 10 * 1024 * 1024;
const PAPER_TYPES = new Set(["QUESTION_PAPER", "MODEL_PAPER"]);
const content = document.getElementById("content");
const filters = { subjectId: "", status: "" };
let subjects = [];
let pageNumber = 0;

function subjectOptions(first) {
  return [["", first], ...subjects.map((subject) => [String(subject.id), `${subject.code} · ${subject.name}`])];
}

/** The details the server expects, built from the form. Exam details go only with a paper. */
function metadataOf(values) {
  return {
    title: values.title,
    description: values.description,
    subjectId: values.subjectId,
    unitId: values.unitId,
    type: values.type,
    questionPaper: PAPER_TYPES.has(values.type)
      ? { examYear: values["questionPaper.examYear"], examType: values["questionPaper.examType"], maxMarks: values["questionPaper.maxMarks"] }
      : null,
  };
}

/** The chosen file, or a message under the file field when it cannot be sent. */
function chosenFile(form) {
  const file = form.querySelector('[name="file"]').files[0];
  let problem = "";
  if (!file) {
    problem = "Choose a PDF file.";
  } else if (file.size > MAX_BYTES) {
    problem = "The file is larger than 10 MB.";
  } else if (!file.name.toLowerCase().endsWith(".pdf")) {
    problem = "Only PDF files can be uploaded.";
  }
  if (problem) {
    throw new ApiError({ status: 400, code: "VALIDATION_FAILED", detail: problem, errors: [{ field: "file", message: problem }] });
  }
  return file;
}

async function fillUnits(unitSelect, subjectId, chosen = "") {
  const whole = h("option", { value: "" }, "Whole subject");
  fill(unitSelect, whole);
  if (!subjectId) {
    return;
  }
  try {
    const units = await api.get(`/subjects/${subjectId}/units`);
    fill(unitSelect, whole, units.map((unit) => h("option", { value: unit.id, selected: String(unit.id) === String(chosen) }, `Unit ${unit.unitNo}: ${unit.title}`)));
  } catch {
    // Without the list, the resource can still be saved for the whole subject.
  }
}

function resourceForm(resource) {
  const paper = resource?.questionPaper;
  const examFields = [
    field("Exam year", input("questionPaper.examYear", { type: "number", min: 2000, max: 2100, value: paper?.examYear || "" })),
    field("Exam type", select("questionPaper.examType", Object.entries(examTypeLabels), paper?.examType || "SEMESTER")),
    field("Maximum marks (optional)", input("questionPaper.maxMarks", { type: "number", min: 1, value: paper?.maxMarks || "" })),
  ];
  const dialog = openForm({
    title: resource ? "Edit resource" : "Upload a resource",
    intro: resource ? "The file itself stays the same. To replace it, delete this resource and upload the new file." : "Students see it as soon as it is uploaded. You can hide it later.",
    submitLabel: resource ? "Save" : "Upload",
    wide: true,
    fields: [
      h("div.field--wide", field("Title", input("title", { value: resource?.title || "", maxlength: 200 }))),
      h("div.field--wide", field("Description (optional)", textarea("description", { maxlength: 1000 }), "Up to 1,000 characters.")),
      field("Subject", numberSelect("subjectId", subjectOptions("Choose a subject"), resource?.subjectId || filters.subjectId)),
      field("Unit", numberSelect("unitId", [["", "Whole subject"]]), "Leave as “Whole subject” for material that covers all units."),
      field("Type", select("type", Object.entries(resourceTypes).map(([value, entry]) => [value, entry.label]), resource?.type || "NOTES")),
      resource ? field("Visibility", select("status", [["PUBLISHED", "Published: students can see it"], ["HIDDEN", "Hidden from students"]], resource.status)) : null,
      ...examFields,
      resource ? null : h("div.field--wide", field("File", input("file", { type: "file", accept: "application/pdf,.pdf" }), "PDF only, up to 10 MB.")),
    ],
    onSubmit: async (values) => {
      if (resource) {
        await api.put("/admin/resources/" + resource.id, { ...metadataOf(values), status: values.status });
        toast("Saved");
      } else {
        await api.upload("/admin/resources", metadataOf(values), chosenFile(dialog));
        toast("Uploaded");
      }
      load();
    },
  });
  dialog.querySelector("textarea").value = resource?.description || "";

  const type = dialog.querySelector('[name="type"]');
  const showExamFields = () => {
    for (const examField of examFields) {
      examField.hidden = !PAPER_TYPES.has(type.value);
    }
  };
  type.addEventListener("change", showExamFields);
  showExamFields();

  const subject = dialog.querySelector('[name="subjectId"]');
  const unit = dialog.querySelector('[name="unitId"]');
  subject.addEventListener("change", () => fillUnits(unit, subject.value));
  fillUnits(unit, subject.value, resource?.unitId || "");
}

/** Everything the server needs to save a resource again, with one thing changed. */
function withStatus(resource, status) {
  const { title, description, subjectId, unitId, type, questionPaper } = resource;
  return { title, description, subjectId, unitId, type, questionPaper, status };
}

async function setHidden(resource, hide) {
  try {
    await api.put("/admin/resources/" + resource.id, withStatus(resource, hide ? "HIDDEN" : "PUBLISHED"));
    toast(hide ? "Hidden from students" : "Published");
    load();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

async function openFile(resource) {
  // The tab opens with the click; the short-lived link is put into it when it arrives.
  const tab = window.open("", "_blank");
  try {
    const link = await api.get(`/resources/${resource.id}/download-url`);
    if (tab) {
      tab.opener = null;
      tab.location.replace(link.url);
    } else {
      window.location.assign(link.url);
    }
  } catch (failure) {
    tab?.close();
    toast(failure.code === "STORAGE_UNAVAILABLE" ? "This file is temporarily unavailable" : messageFor(failure));
  }
}

async function remove(resource) {
  const yes = await confirmAction({
    title: "Delete this resource?",
    text: `"${resource.title}" and its file are removed for everyone, together with the bookmarks students made. This cannot be undone.`,
    confirmLabel: "Delete",
    danger: true,
  });
  if (!yes) {
    return;
  }
  try {
    await api.delete("/admin/resources/" + resource.id);
    toast("Deleted");
    load();
  } catch (failure) {
    toast(failure.code === "STORAGE_UNAVAILABLE" ? "File storage is unavailable, so nothing was deleted" : messageFor(failure));
  }
}

function place(resource) {
  return [resource.subjectCode, resource.unitNo ? "Unit " + resource.unitNo : "Whole subject", examText(resource)].filter(Boolean).join(" · ");
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ size: 20, page: pageNumber });
  if (filters.subjectId) {
    params.set("subjectId", filters.subjectId);
  }
  if (filters.status) {
    params.set("status", filters.status);
  }
  loadInto(list, () => api.get("/admin/resources?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 resource" : page.totalElements + " resources"),
      dataTable(
        [
          { label: "Resource", cell: (resource) => h("div.stack.stack--tight", h("strong", resource.title), h("span.row__meta", place(resource))) },
          { label: "Type", cell: (resource) => typeTag(resource.type) },
          { label: "Size", cell: (resource) => h("span.num", formatSize(resource.fileSizeBytes)) },
          { label: "Uploaded", cell: (resource) => formatDate(resource.createdAt) },
          { label: "Status", cell: (resource) => (resource.status === "PUBLISHED" ? tag("Published", "success", "eye") : tag("Hidden", "", "lock")) },
          {
            label: "Actions",
            align: "end",
            cell: (resource) =>
              h(
                "span.row-actions",
                rowAction("Open the file: " + resource.title, "external", () => openFile(resource)),
                resource.status === "PUBLISHED"
                  ? rowAction("Hide from students: " + resource.title, "lock", () => setHidden(resource, true))
                  : rowAction("Publish: " + resource.title, "eye", () => setHidden(resource, false)),
                rowAction("Edit: " + resource.title, "edit", () => resourceForm(resource)),
                rowAction("Delete: " + resource.title, "trash", () => remove(resource), "danger"),
              ),
          },
        ],
        page.content,
        filters.subjectId || filters.status ? "No resources match these filters." : "No resources yet. Upload the first one with the button above.",
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
  subject.addEventListener("change", () => {
    filters.subjectId = subject.value;
    pageNumber = 0;
    load();
  });
  const status = select("status-filter", [["", "Published and hidden"], ["PUBLISHED", "Published"], ["HIDDEN", "Hidden"]]);
  status.addEventListener("change", () => {
    filters.status = status.value;
    pageNumber = 0;
    load();
  });
  return h("section.card.toolbar-card", { "aria-label": "Filters" }, h("div.filters-row", field("Subject", subject), field("Status", status)));
}

function start(loaded) {
  subjects = loaded.filter((subject) => subject.active);
  if (!subjects.length) {
    fill(content, h("div.card", empty("resources", "No subjects yet", "Add a subject before uploading resources.", h("a.button.button--primary.button--small", { href: "/admin/subjects.html" }, "Go to subjects"))));
    return;
  }
  const add = h("button.button.button--primary", { type: "button" }, icon("upload"), "Upload resource");
  add.addEventListener("click", () => resourceForm(null));
  fill(document.getElementById("actions"), add);
  fill(content, reportsBox, filterBar(), h("section.card", { id: "list", "aria-label": "Resources" }, h("div.skeleton")));
  loadReports();
  load();
}

// FR-LIB-005: what students reported about resources. Only open reports are shown; resolving one
// takes it off the list.
const reportsBox = h("section.card", { "aria-label": "Reported resources" });

function loadReports() {
  loadInto(reportsBox, () => api.get("/admin/resource-reports?status=OPEN&size=50"), (page) => {
    const columns = [
      { label: "Resource", cell: (report) => report.resourceTitle || "(deleted)" },
      { label: "What is wrong", cell: (report) => report.reason },
      { label: "Reported by", cell: (report) => h("span.mono", report.reportedBy || "—") },
      { label: "Date", cell: (report) => formatDate(report.createdAt) },
      { label: "", align: "end", cell: (report) => rowAction("Resolve", "check", () => resolveReport(report)) },
    ];
    fill(
      reportsBox,
      h("div.card__head", h("h2.card__title", icon("alert"), "Reported resources"), page.totalElements ? tag(`${page.totalElements} open`, "warning") : null),
      dataTable(columns, page.content, "No open reports. Students can report a problem from a resource's page."),
    );
  });
}

async function resolveReport(report) {
  try {
    await api.post(`/admin/resource-reports/${report.id}/resolve`);
    toast("Report resolved");
    loadReports();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

loadInto(content, () => api.get("/admin/subjects"), start);
