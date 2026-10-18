import { confirmAction, dataTable, dialogShell, field, hidePageError, input, numberSelect, openForm, pager, rowAction, select } from "./admin-kit.js";
import { api, ApiError, messageFor } from "./api.js";
import { fill, formatSize, h, icon, tag, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: previous-year papers. Import a whole organised folder (BRANCH/Subject/2025(December)-supply.pdf),
// add one paper by hand, correct its details, hide it from students, or remove it. The server checks
// every file and every detail again; the checks here only save a wasted upload.

const MAX_BYTES = 10 * 1024 * 1024;
const PAUSE_MS = 650; // The server allows 120 requests a minute for one login; this stays well inside it.
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const KIND_OPTIONS = [["REGULAR", "Regular"], ["SUPPLY", "Supplementary"], ["MID", "Mid"]];
const filters = { branch: "", status: "", q: "" };
let pageNumber = 0;
const content = document.getElementById("content");

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function sitting(paper) {
  const kind = KIND_OPTIONS.find(([value]) => value === paper.kind)[1] + (paper.kind === "MID" && paper.midNumber ? " " + paper.midNumber : "");
  return [`${paper.monthName} ${paper.year}`, kind, paper.regulation, paper.variant > 1 ? "set " + paper.variant : null].filter(Boolean).join(" · ");
}

/** What the server expects, from the form. A mid number goes only with a mid paper. */
function metadataOf(values) {
  return {
    branch: values.branch,
    subject: values.subject,
    year: values.year,
    month: values.month,
    kind: values.kind,
    midNumber: values.kind === "MID" ? values.midNumber : null,
    regulation: values.regulation,
    courseCode: values.courseCode,
    variant: values.variant || 1,
  };
}

// ---------------------------------------------------------------- Add or edit one paper
function paperForm(paper) {
  const dialog = openForm({
    title: paper ? "Edit paper" : "Add a paper",
    intro: paper ? "The file stays the same. To replace it, delete this paper and add the new file." : "Students see it as soon as it is added. You can hide it later.",
    submitLabel: paper ? "Save" : "Add",
    wide: true,
    fields: [
      field("Branch", input("branch", { value: paper?.branch || filters.branch, maxlength: 10 }), "For example CSE, ECE, EEE, MECH, CIVIL or FE."),
      h("div.field--wide", field("Subject", input("subject", { value: paper?.subject || "", maxlength: 120 }))),
      field("Exam year", input("year", { type: "number", min: 2000, max: 2100, value: paper?.year || new Date().getFullYear() })),
      field("Month", numberSelect("month", MONTHS.map((name, index) => [index + 1, name]), paper?.month || 12)),
      field("Exam", select("kind", KIND_OPTIONS, paper?.kind || "REGULAR")),
      field("Mid number (mid papers)", input("midNumber", { type: "number", min: 1, max: 4, value: paper?.midNumber || "" }), "Leave empty if the paper does not say."),
      field("Regulation (optional)", input("regulation", { value: paper?.regulation || "", maxlength: 4 }), "For example AR20."),
      field("Course code (optional)", input("courseCode", { value: paper?.courseCode || "", maxlength: 40 })),
      field("Question set", input("variant", { type: "number", min: 1, max: 20, value: paper?.variant || 1 }), "Use 2, 3 … when one sitting had more than one question set."),
      paper ? field("Visibility", select("status", [["PUBLISHED", "Published: students can see it"], ["HIDDEN", "Hidden from students"]], paper.status)) : null,
      paper ? null : h("div.field--wide", field("File", input("file", { type: "file", accept: "application/pdf,.pdf" }), "PDF only, up to 10 MB.")),
    ],
    onSubmit: async (values) => {
      if (paper) {
        await api.put("/admin/pyqs/" + paper.id, { metadata: metadataOf(values), status: values.status });
        toast("Saved");
      } else {
        const file = dialog.querySelector('[name="file"]').files[0];
        if (!file || !file.name.toLowerCase().endsWith(".pdf") || file.size > MAX_BYTES) {
          throw new ApiError({ status: 400, code: "VALIDATION_FAILED", detail: "Choose a PDF file of at most 10 MB.", errors: [] });
        }
        await api.upload("/admin/pyqs", metadataOf(values), file);
        toast("Added");
      }
      load();
    },
  });
}

async function setHidden(paper, hide) {
  try {
    await api.put("/admin/pyqs/" + paper.id, {
      metadata: { branch: paper.branch, subject: paper.subject, year: paper.year, month: paper.month, kind: paper.kind, midNumber: paper.midNumber, regulation: paper.regulation, courseCode: paper.courseCode, variant: paper.variant },
      status: hide ? "HIDDEN" : "PUBLISHED",
    });
    toast(hide ? "Hidden from students" : "Published");
    load();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

async function remove(paper) {
  const yes = await confirmAction({
    title: "Delete this paper?",
    text: `${paper.subject}, ${sitting(paper)}, and its file are removed for everyone, together with the saves students made. This cannot be undone.`,
    confirmLabel: "Delete",
    danger: true,
  });
  if (!yes) {
    return;
  }
  try {
    await api.delete("/admin/pyqs/" + paper.id);
    toast("Deleted");
    load();
  } catch (failure) {
    toast(failure.code === "STORAGE_UNAVAILABLE" ? "File storage is unavailable, so nothing was deleted" : messageFor(failure));
  }
}

async function openFile(paper) {
  try {
    const link = await api.get(`/admin/pyqs/${paper.id}/link`);
    window.open(link.url, "_blank", "noopener");
  } catch (failure) {
    toast(failure.code === "STORAGE_UNAVAILABLE" ? "File storage is unavailable right now" : messageFor(failure));
  }
}

// ---------------------------------------------------------------- Import a folder
function importFolder() {
  const dialog = dialogShell("import-title");
  dialog.classList.add("dialog--wide");
  const picker = input("folder", { type: "file", webkitdirectory: "", multiple: "", accept: "application/pdf,.pdf" });
  const summary = h("div.stack.stack--tight", { "aria-live": "polite" });
  const progress = h("progress.import__progress", { max: 1, value: 0, hidden: true, "aria-label": "Import progress" });
  const status = h("p.row__meta.num", { "aria-live": "polite" });
  const problems = h("ul.import__problems", { "aria-label": "Files that were not added", hidden: true });
  const start = h("button.button.button--primary", { type: "button", disabled: true }, icon("upload"), "Start import");
  const stop = h("button.button.button--quiet", { type: "button", hidden: true }, "Stop");
  const close = h("button.button.button--quiet", { type: "button", "data-close": true }, "Close");
  let files = [];
  let stopped = false;
  let running = false;

  picker.addEventListener("change", () => {
    // Skip the check folder, the report, and the hidden "._" copies a Mac makes on some drives.
    files = [...picker.files].filter((file) => /\.pdf$/i.test(file.name) && !file.name.startsWith("._") && !file.webkitRelativePath.split("/").some((part) => part.startsWith("_")));
    const oversize = files.filter((file) => file.size > MAX_BYTES).length;
    const branches = new Map();
    for (const file of files) {
      const parts = file.webkitRelativePath.split("/");
      const branch = parts.length >= 3 ? parts[parts.length - 3] : "?";
      branches.set(branch, (branches.get(branch) || 0) + 1);
    }
    fill(
      summary,
      files.length ? h("p", h("strong", String(files.length)), " PDF files found.") : h("p.muted", "No PDF files found. Choose the sorted folder, the one that holds the branch folders."),
      files.length ? h("p.row__meta", [...branches].map(([branch, count]) => `${branch}: ${count}`).join(" · ")) : null,
      oversize ? h("p.row__meta", icon("alert"), ` ${oversize} file${oversize === 1 ? " is" : "s are"} over 10 MB and will be skipped.`) : null,
    );
    start.disabled = !files.length;
  });

  start.addEventListener("click", async () => {
    running = true;
    stopped = false;
    start.disabled = true;
    picker.disabled = true;
    stop.hidden = false;
    progress.hidden = false;
    fill(problems);
    problems.hidden = true;
    const result = { added: 0, already: 0, failed: 0 };
    const addProblem = (file, message) => {
      problems.hidden = false;
      problems.append(h("li.import__problem", h("span.import__where", file.webkitRelativePath.split("/").slice(-3).join(" / ")), h("span", message)));
    };
    let done = 0;
    for (const file of files) {
      if (stopped) {
        break;
      }
      if (file.size > MAX_BYTES) {
        result.failed += 1;
        addProblem(file, "Larger than 10 MB.");
      } else {
        const form = new FormData();
        form.append("path", file.webkitRelativePath);
        form.append("file", file);
        for (;;) {
          try {
            await api.postForm("/admin/pyqs", form);
            result.added += 1;
            break;
          } catch (failure) {
            if (failure instanceof ApiError && failure.status === 429) {
              // Too many requests in a minute: wait as long as the server says, then try the same file again.
              await sleep((failure.retryAfter || 10) * 1000 + 500);
              continue;
            }
            if (failure instanceof ApiError && failure.code === "CONFLICT") {
              result.already += 1;
            } else if (failure instanceof ApiError && (failure.code === "STORAGE_UNAVAILABLE" || failure.status === 0)) {
              stopped = true;
              addProblem(file, messageFor(failure) + " The import stopped. Fix it and start again: papers already added are skipped.");
            } else {
              result.failed += 1;
              addProblem(file, failure.fieldErrors?.length ? failure.fieldErrors[0].message : messageFor(failure));
            }
            break;
          }
        }
        await sleep(PAUSE_MS);
      }
      done += 1;
      progress.value = done / files.length;
      status.textContent = `${done} of ${files.length} · ${result.added} added · ${result.already} already there · ${result.failed} problems`;
    }
    running = false;
    stop.hidden = true;
    picker.disabled = false;
    start.disabled = false;
    status.textContent += stopped ? " · stopped" : " · finished";
    toast(stopped ? "Import stopped" : `Import finished: ${result.added} added`);
    load();
  });
  stop.addEventListener("click", () => {
    stopped = true;
    stop.disabled = true;
  });
  window.addEventListener("beforeunload", (event) => {
    if (running) {
      event.preventDefault();
    }
  });

  fill(
    dialog,
    h(
      "div.stack.stack--loose",
      h("div.stack.stack--tight", h("h2", { id: "import-title" }, "Import a folder of papers"), h("p.muted", "Choose the organised folder: branch folders, then subject folders, then files named like 2025(December)-supply AR20.pdf. The branch, subject and exam come from the folders and the file name.")),
      field("Folder", picker, "Your browser asks to upload the folder's files; nothing leaves the computer except the PDFs you start importing."),
      summary,
      h("p.muted", "Keep this page open while it runs: about two papers a second. If it is interrupted, start it again. Papers already added are skipped."),
      progress,
      status,
      problems,
      h("div.dialog__actions", close, stop, start),
    ),
  );
  dialog.showModal();
}

// ---------------------------------------------------------------- The list
function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ size: 20, page: pageNumber });
  for (const [name, value] of Object.entries(filters)) {
    if (value) {
      params.set(name, value);
    }
  }
  loadInto(list, () => api.get("/admin/pyqs?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 paper" : page.totalElements + " papers"),
      dataTable(
        [
          { label: "Subject", cell: (paper) => h("div.stack.stack--tight", h("strong", paper.subject), h("span.row__meta", paper.branch)) },
          { label: "Sitting", cell: (paper) => sitting(paper) },
          { label: "Size", cell: (paper) => h("span.num", formatSize(paper.sizeBytes)) },
          { label: "Downloads", cell: (paper) => h("span.num", String(paper.downloads)) },
          { label: "Status", cell: (paper) => (paper.status === "PUBLISHED" ? tag("Published", "success", "eye") : tag("Hidden", "", "lock")) },
          {
            label: "Actions",
            align: "end",
            cell: (paper) =>
              h(
                "span.row-actions",
                rowAction("Open the file: " + paper.subject, "external", () => openFile(paper)),
                paper.status === "PUBLISHED" ? rowAction("Hide from students: " + paper.subject, "lock", () => setHidden(paper, true)) : rowAction("Publish: " + paper.subject, "eye", () => setHidden(paper, false)),
                rowAction("Edit: " + paper.subject, "edit", () => paperForm(paper)),
                rowAction("Delete: " + paper.subject, "trash", () => remove(paper), "danger"),
              ),
          },
        ],
        page.content,
        Object.values(filters).some(Boolean) ? "No papers match these filters." : "No papers yet. Import a folder with the button above.",
      ),
      pager(page, (target) => {
        pageNumber = target;
        load();
      }),
    );
  });
}

function filterBar() {
  let timer = 0;
  const q = h("input.search__input", { type: "search", id: "pyq-admin-search", placeholder: "Search subject, code or year", autocomplete: "off" });
  q.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      filters.q = q.value.trim().length >= 2 ? q.value.trim() : "";
      pageNumber = 0;
      load();
    }, 300);
  });
  const branch = input("branch-filter", { placeholder: "Any", maxlength: 10 });
  branch.addEventListener("input", () => {
    filters.branch = branch.value.trim().toUpperCase();
    pageNumber = 0;
    clearTimeout(timer);
    timer = setTimeout(load, 300);
  });
  const status = select("status-filter", [["", "Published and hidden"], ["PUBLISHED", "Published"], ["HIDDEN", "Hidden"]]);
  status.addEventListener("change", () => {
    filters.status = status.value;
    pageNumber = 0;
    load();
  });
  return h("section.card.toolbar-card", { "aria-label": "Search and filters" }, h("label.search", { for: "pyq-admin-search" }, h("span.visually-hidden", "Search papers"), icon("search"), q), h("div.filters-row", field("Branch", branch), field("Status", status)));
}

const importButton = h("button.button.button--primary", { type: "button" }, icon("upload"), "Import folder");
importButton.addEventListener("click", importFolder);
const addButton = h("button.button.button--secondary", { type: "button" }, icon("plus"), "Add paper");
addButton.addEventListener("click", () => paperForm(null));
fill(document.getElementById("actions"), h("div.line", addButton, importButton));
fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Papers" }, h("div.skeleton")));
load();
