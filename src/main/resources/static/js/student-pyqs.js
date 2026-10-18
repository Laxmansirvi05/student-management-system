import { api, messageFor } from "./api.js";
import { fill, formatSize, h, icon, tag, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Past papers (PYQs): previous-year question papers by branch and subject. Browse by subject, search
// across all papers, filter by year, exam and regulation, open a paper in the page, download it, and
// save the ones that matter. The server does the searching; this page sends filters and draws.

const PAGE_SIZE = 20;
const SEARCH_DELAY = 300;
const RECENT_KEY = "pyq.recent";
const KINDS = { REGULAR: ["Regular", "primary"], SUPPLY: ["Supplementary", "warning"], MID: ["Mid", "success"] };
const state = { view: "subjects", branch: "", subject: null, query: "", year: "", kind: "", regulation: "", sort: "newest", page: 0 };
let overview = { branches: [], years: [], regulations: [], totalPapers: 0 };
let myBranch = "";

const content = document.getElementById("content");
const search = h("input.search__input", { type: "search", id: "pyq-search", placeholder: "Search a subject, course code or year", autocomplete: "off", maxlength: 100 });
const branchBar = h("div.pills", { role: "group", "aria-label": "Branch" });
const viewBar = h("div.pills", { role: "group", "aria-label": "View" });
const filterBar = h("div.filters");
const list = h("div.stack.stack--loose", { id: "list", "aria-live": "polite" }, h("div.skeleton.skeleton--card"));

// ---------------------------------------------------------------- Helpers
function kindLabel(paper) {
  const [label] = KINDS[paper.kind];
  return paper.kind === "MID" && paper.midNumber ? `${label} ${paper.midNumber}` : label;
}

/** "December 2025 · Supplementary · AR20 · set 2" */
function sittingText(paper) {
  return [`${paper.monthName} ${paper.year}`, kindLabel(paper), paper.regulation, paper.variant > 1 ? "set " + paper.variant : null].filter(Boolean).join(" · ");
}

/** "I B.Tech", "CSE(AI&ML)" and so on become the library's branch codes. */
function libraryBranchOf(text) {
  const code = String(text || "").toUpperCase().replace(/\(.*$/, "").replace(/[^A-Z]/g, "");
  const map = { CSE: "CSE", CS: "CSE", ECE: "ECE", EEE: "EEE", ME: "MECH", MECH: "MECH", CE: "CIVIL", CIVIL: "CIVIL", IT: "IT", FE: "FE" };
  return map[code] || "";
}

function readRecent() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
  } catch {
    return [];
  }
}

function rememberOpened(paper) {
  try {
    const kept = readRecent().filter((entry) => entry.id !== paper.id);
    kept.unshift({ id: paper.id, branch: paper.branch, subject: paper.subject, sitting: sittingText(paper) });
    localStorage.setItem(RECENT_KEY, JSON.stringify(kept.slice(0, 12)));
  } catch {
    // Without browser storage the page still works; it just does not remember.
  }
}

// ---------------------------------------------------------------- Opening a paper
function openPaper(paper) {
  const frame = h("iframe.pyq-viewer__frame", { title: `${paper.subject}, ${sittingText(paper)}` });
  const message = h("p.row__meta", "Opening the paper…");
  const download = h("button.button.button--secondary.button--small", { type: "button" }, icon("download"), "Download");
  download.addEventListener("click", () => downloadPaper(paper));
  const dialog = h(
    "dialog.dialog.dialog--viewer",
    { "aria-labelledby": "pyq-view-title" },
    h("div.line", h("div.stack.stack--tight", h("h2", { id: "pyq-view-title" }, paper.subject), h("p.row__meta", `${paper.branch} · ${sittingText(paper)}`)), h("div.line", download, h("button.button.button--quiet.button--small", { type: "button", "data-close": true }, "Close"))),
    message,
    frame,
  );
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog || event.target.closest("[data-close]")) {
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => dialog.remove());
  document.body.append(dialog);
  dialog.showModal();
  api
    .get(`/pyqs/${paper.id}/link`)
    .then((link) => {
      frame.src = link.url;
      message.hidden = true;
      rememberOpened(paper);
    })
    .catch((failure) => {
      message.textContent = messageFor(failure);
      frame.hidden = true;
    });
}

async function downloadPaper(paper) {
  try {
    const link = await api.get(`/pyqs/${paper.id}/link?download=true`);
    const anchor = h("a", { href: link.url, download: "" });
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    rememberOpened(paper);
    paper.downloads += 1;
  } catch (failure) {
    toast(messageFor(failure));
  }
}

// ---------------------------------------------------------------- A paper in a list
function saveButton(paper, onChange) {
  const button = h("button.icon-button.icon-button--bare", { type: "button" }, icon("bookmark"));
  const show = () => {
    button.setAttribute("aria-pressed", String(paper.bookmarked));
    button.setAttribute("aria-label", (paper.bookmarked ? "Remove from saved: " : "Save: ") + paper.subject + ", " + sittingText(paper));
  };
  show();
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await (paper.bookmarked ? api.delete(`/pyqs/${paper.id}/save`) : api.put(`/pyqs/${paper.id}/save`));
      paper.bookmarked = !paper.bookmarked;
      show();
      toast(paper.bookmarked ? "Saved" : "Removed from saved");
      onChange?.(paper);
    } catch (failure) {
      toast(messageFor(failure));
    } finally {
      button.disabled = false;
    }
  });
  return button;
}

function paperRow(paper, { showSubject = false, onSaveChange } = {}) {
  const [, kindColour] = KINDS[paper.kind];
  const open = h("button.button.button--secondary.button--small", { type: "button" }, icon("eye"), "View");
  open.addEventListener("click", () => openPaper(paper));
  const download = h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Download ${paper.subject}, ${sittingText(paper)}` }, icon("download"));
  download.addEventListener("click", () => downloadPaper(paper));
  return h(
    "li.row.pyq-row",
    h("span.badge-icon.badge-icon--danger", icon("file")),
    h(
      "div.stack.stack--tight.pyq-row__text",
      h("span.row__title", showSubject ? paper.subject : `${paper.monthName} ${paper.year}`),
      h("span.row__meta", showSubject ? `${paper.branch} · ${sittingText(paper)}` : [kindLabel(paper), paper.regulation, paper.variant > 1 ? "set " + paper.variant : null].filter(Boolean).join(" · "), " · ", h("span.num", formatSize(paper.sizeBytes))),
    ),
    tag(kindLabel(paper), kindColour),
    h("div.pyq-row__actions", open, download, saveButton(paper, onSaveChange)),
  );
}

function pager(page) {
  const button = (label, iconName, target, before) => {
    const element = h("button.button.button--secondary.button--small", { type: "button", disabled: target < 0 || target >= page.totalPages }, before ? icon(iconName) : null, label, before ? null : icon(iconName));
    element.addEventListener("click", () => {
      state.page = target;
      load();
      list.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return element;
  };
  return h("nav.pager", { "aria-label": "Pages" }, button("Previous", "arrow-left", page.page - 1, true), h("span.row__meta.num", "Page ", h("strong", page.page + 1), " of " + page.totalPages), button("Next", "arrow-right", page.page + 1, false));
}

// ---------------------------------------------------------------- Loading
let latest = 0;

function query(extra = {}) {
  const params = new URLSearchParams({ page: state.page, size: PAGE_SIZE, ...extra });
  const subject = state.subject;
  if (state.branch || subject) {
    params.set("branch", subject ? subject.branch : state.branch);
  }
  if (subject) {
    params.set("subject", subject.name);
  }
  if (state.query.length >= 2) {
    params.set("q", state.query);
  }
  for (const [name, value] of [["year", state.year], ["kind", state.kind], ["regulation", state.regulation], ["sort", state.sort]]) {
    if (value) {
      params.set(name, value);
    }
  }
  return params.toString();
}

function subjectCard(subject) {
  const years = subject.firstYear === subject.latestYear ? String(subject.latestYear) : `${subject.firstYear}–${subject.latestYear}`;
  const card = h(
    "button.card.card--choice.card--lift",
    { type: "button" },
    h("div.line", h("span.tag.mono", subject.branch), h("span.row__meta.num", years)),
    h("h3.subject__name", subject.subject),
    h("p.row__meta.num", subject.papers === 1 ? "1 paper" : subject.papers + " papers"),
    h("span.more", "Open", icon("chevron-right")),
  );
  card.addEventListener("click", () => {
    state.subject = { name: subject.subject, branch: subject.branch };
    state.page = 0;
    render();
  });
  return card;
}

function loadSubjects() {
  const mine = ++latest;
  const params = new URLSearchParams();
  if (state.branch) {
    params.set("branch", state.branch);
  }
  if (state.query.length >= 2) {
    params.set("q", state.query);
  }
  loadInto(list, () => api.get("/pyqs/subjects?" + params), (subjects) => {
    if (mine !== latest) {
      return;
    }
    fill(
      list,
      h("p.row__meta.num", subjects.length === 1 ? "1 subject" : subjects.length + " subjects"),
      subjects.length ? h("div.grid.grid--3", subjects.map(subjectCard)) : h("div.card", empty("search", "No subjects match", "Try another branch or a shorter search.")),
    );
  });
}

function loadSubjectPapers() {
  const mine = ++latest;
  loadInto(list, () => api.get("/pyqs?" + query({ size: 100 })), (page) => {
    if (mine !== latest) {
      return;
    }
    const back = h("button.button.button--quiet.button--small", { type: "button" }, icon("arrow-left"), "All subjects");
    back.addEventListener("click", () => {
      state.subject = null;
      state.year = state.kind = state.regulation = "";
      render();
    });
    const years = [...new Set(page.content.map((paper) => paper.year))];
    fill(
      list,
      h("div.line", h("div.stack.stack--tight", h("p.eyebrow", state.subject.branch), h("h2", state.subject.name), h("p.row__meta.num", page.totalElements === 1 ? "1 paper" : page.totalElements + " papers")), back),
      page.content.length
        ? years.map((year) =>
            h("section.card", { "aria-label": String(year) }, h("h3.pyq-year", String(year)), h("ul.rows", page.content.filter((paper) => paper.year === year).map((paper) => paperRow(paper)))),
          )
        : h("div.card", empty("search", "No papers match these filters", "Clear a filter to see more.")),
    );
  });
}

function loadPapers() {
  const mine = ++latest;
  loadInto(list, () => api.get("/pyqs?" + query()), (page) => {
    if (mine !== latest) {
      return;
    }
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 paper" : page.totalElements + " papers"),
      page.content.length
        ? [h("section.card", h("ul.rows", page.content.map((paper) => paperRow(paper, { showSubject: true })))), page.totalPages > 1 ? pager(page) : null]
        : h("div.card", empty("search", "No papers match", "Try a different search or fewer filters.")),
    );
  });
}

function loadSaved() {
  const mine = ++latest;
  loadInto(list, () => api.get(`/pyqs/saved?page=${state.page}&size=${PAGE_SIZE}`), (page) => {
    if (mine !== latest) {
      return;
    }
    fill(
      list,
      page.content.length
        ? [h("section.card", h("ul.rows", page.content.map((paper) => paperRow(paper, { showSubject: true, onSaveChange: () => loadSaved() })))), page.totalPages > 1 ? pager(page) : null]
        : h("div.card", empty("bookmark", "Nothing saved yet", "Tap the bookmark on a paper to keep it here.")),
    );
  });
}

function showRecent() {
  latest += 1;
  const recent = readRecent();
  fill(
    list,
    recent.length
      ? h(
          "section.card",
          h("ul.rows", recent.map((entry) => {
            const open = h("button.button.button--secondary.button--small", { type: "button" }, icon("eye"), "Open");
            open.addEventListener("click", () =>
              openPaper({ id: entry.id, branch: entry.branch, subject: entry.subject, monthName: entry.sitting, year: "", kind: "REGULAR", variant: 1, regulation: null, downloads: 0, bookmarked: false, sizeBytes: 0 }),
            );
            return h("li.row.pyq-row", h("span.badge-icon.badge-icon--neutral", icon("history")), h("div.stack.stack--tight.pyq-row__text", h("span.row__title", entry.subject), h("span.row__meta", `${entry.branch} · ${entry.sitting}`)), h("div.pyq-row__actions", open));
          })),
        )
      : h("div.card", empty("history", "Nothing opened yet", "Papers you open or download on this device show up here.")),
  );
}

// ---------------------------------------------------------------- The controls
function option(value, label) {
  return h("option", { value }, label);
}

function select(label, id, options, current, onChange) {
  const element = h("select.select", { id }, options.map(([value, text]) => option(value, text)));
  element.value = current;
  element.addEventListener("change", () => onChange(element.value));
  return h("label.filter", { for: id }, h("span.visually-hidden", label), element);
}

function drawFilters() {
  const on = state.view === "papers" || state.subject;
  filterBar.hidden = !on;
  if (!on) {
    fill(filterBar);
    return;
  }
  const change = (name) => (value) => {
    state[name] = value;
    state.page = 0;
    load();
  };
  const reset = h("button.button.button--quiet.button--small", { type: "button" }, icon("refresh"), "Clear filters");
  reset.addEventListener("click", () => {
    Object.assign(state, { year: "", kind: "", regulation: "", sort: "newest", page: 0 });
    drawFilters();
    load();
  });
  fill(
    filterBar,
    select("Year", "pyq-year", [["", "Any year"], ...overview.years.map((year) => [String(year), String(year)])], state.year, change("year")),
    select("Exam", "pyq-kind", [["", "Any exam"], ["REGULAR", "Regular"], ["SUPPLY", "Supplementary"], ["MID", "Mid"]], state.kind, change("kind")),
    select("Regulation", "pyq-reg", [["", "Any regulation"], ...overview.regulations.map((regulation) => [regulation, regulation])], state.regulation, change("regulation")),
    select("Sort", "pyq-sort", [["newest", "Newest first"], ["oldest", "Oldest first"], ["popular", "Most downloaded"], ["subject", "Subject A–Z"]], state.sort, change("sort")),
    reset,
  );
}

function drawBranches() {
  const all = [["", `All branches · ${overview.totalPapers}`]];
  const ordered = [...overview.branches].sort((a, b) => (a.branch === myBranch ? -1 : b.branch === myBranch ? 1 : a.branch.localeCompare(b.branch)));
  fill(
    branchBar,
    [...all, ...ordered.map((entry) => [entry.branch, `${entry.branch === myBranch ? "★ " : ""}${entry.branch} · ${entry.papers}`])].map(([value, label]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(state.branch === value && !state.subject) }, label);
      pill.addEventListener("click", () => {
        state.branch = value;
        state.subject = null;
        state.page = 0;
        render();
      });
      return pill;
    }),
  );
}

function drawViews() {
  const views = [["subjects", "Subjects"], ["papers", "All papers"], ["saved", "Saved"], ["recent", "Recently opened"]];
  fill(
    viewBar,
    views.map(([value, label]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(state.view === value) }, label);
      pill.addEventListener("click", () => {
        state.view = value;
        state.subject = null;
        state.page = 0;
        render();
      });
      return pill;
    }),
  );
}

function load() {
  if (state.view === "saved") {
    loadSaved();
  } else if (state.view === "recent") {
    showRecent();
  } else if (state.subject) {
    loadSubjectPapers();
  } else if (state.view === "papers") {
    loadPapers();
  } else {
    loadSubjects();
  }
}

function render() {
  drawViews();
  drawBranches();
  drawFilters();
  const browsing = state.view === "subjects" || state.view === "papers";
  branchBar.hidden = !browsing || Boolean(state.subject);
  load();
}

// ---------------------------------------------------------------- Start
let waiting = 0;
search.addEventListener("input", () => {
  state.query = search.value.trim();
  if (state.view === "saved" || state.view === "recent") {
    state.view = state.query ? "papers" : "subjects";
  }
  state.page = 0;
  clearTimeout(waiting);
  waiting = setTimeout(render, SEARCH_DELAY);
});

// "/" jumps to the search box, as on most sites.
document.addEventListener("keydown", (event) => {
  const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName || "");
  if (event.key === "/" && !typing && !event.metaKey && !event.ctrlKey) {
    event.preventDefault();
    search.focus();
  }
});

fill(
  content,
  h("section.card", { "aria-label": "Search and filters" }, h("label.search.search--large", { for: "pyq-search" }, h("span.visually-hidden", "Search past papers"), icon("search"), search), viewBar, branchBar, filterBar),
  list,
);

Promise.allSettled([api.get("/pyqs/overview"), api.get("/students/me")]).then(([overviewResult, studentResult]) => {
  if (overviewResult.status === "fulfilled") {
    overview = overviewResult.value;
  }
  if (studentResult.status === "fulfilled") {
    myBranch = libraryBranchOf(studentResult.value.branch);
    // A first-year student's papers are the first-year ones.
    if (studentResult.value.currentSemester <= 2 && overview.branches.some((entry) => entry.branch === "FE")) {
      myBranch = "FE";
    }
    if (myBranch && overview.branches.some((entry) => entry.branch === myBranch)) {
      state.branch = myBranch;
    }
  }
  if (!overview.totalPapers) {
    fill(list, h("div.card", empty("book-open", "No past papers yet", "Previous-year question papers appear here once the college adds them.")));
    drawViews();
    return;
  }
  render();
});
