import { api } from "./api.js";
import { fill, h, icon, stagger } from "./dom.js";
import { reveal } from "./motion.js";
import { empty, loadInto, resourceCard, resourceTypes } from "./parts.js";
import "./shell.js";

// The resource hub (FR-LIB-001, FR-LIB-002): subject folders first; a folder shows that subject's
// resources grouped by unit. A search or a type filter lists matching resources from every subject.
// The server does the searching; this page only sends the filters and draws what comes back.
// Students can read and bookmark; uploading belongs to the admin side.

const PAGE_SIZE = 12;
const SEARCH_DELAY = 300;
const state = { query: "", subjectId: "", unitId: "", type: "", page: 0 };

function option(value, label) {
  return h("option", { value }, label);
}

function field(id, label, options) {
  const select = h("select.select", { id }, options);
  return { select, element: h("label.filter", { for: id }, h("span.visually-hidden", label), select) };
}

const search = h("input.search__input", { type: "search", id: "resource-search", placeholder: "Search notes, papers and manuals", autocomplete: "off", maxlength: 100 });
const subjectField = field("filter-subject", "Subject", [option("", "All subjects")]);
const unitField = field("filter-unit", "Unit", [option("", "All units")]);
const typeField = field("filter-type", "Type", [option("", "All types"), ...Object.entries(resourceTypes).map(([value, entry]) => option(value, entry.label))]);
const count = h("p.row__meta.num", { "aria-live": "polite" });
const reset = h("button.button.button--quiet.button--small", { type: "button" }, icon("refresh"), "Clear filters");
const list = h("div.stack.stack--loose", { id: "list" }, h("div.skeleton.skeleton--card"));

/** The filters as a query string. A search of one letter is left out, as the server asks for two. */
function query(size = PAGE_SIZE) {
  const params = new URLSearchParams({ page: state.page, size });
  if (state.query.length >= 2) {
    params.set("q", state.query);
  }
  for (const [name, value] of [["subjectId", state.subjectId], ["unitId", state.unitId], ["type", state.type]]) {
    if (value) {
      params.set(name, value);
    }
  }
  return params.toString();
}

function filtered() {
  return state.query.length >= 2 || state.subjectId || state.unitId || state.type;
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
  return h(
    "nav.pager",
    { "aria-label": "Pages" },
    button("Previous", "arrow-left", page.page - 1, true),
    h("span.row__meta.num", "Page ", h("strong", page.page + 1), " of " + page.totalPages),
    button("Next", "arrow-right", page.page + 1, false),
  );
}

function nothingFound() {
  if (!filtered()) {
    return h("div.card", empty("resources", "No resources yet", "Notes, papers and manuals appear here once they are uploaded."));
  }
  const clear = h("button.button.button--primary.button--small", { type: "button" }, "Clear filters");
  clear.addEventListener("click", clearFilters);
  return h("div.card", empty("search", "No resources match your filters", "Try a different search or fewer filters.", clear));
}

// Each load gets a number. An answer that arrives after a newer request was sent is dropped, so
// fast typing can never show the results of an older search.
let latest = 0;

/** The subject folders, newest semester first, as cards to open. */
function folders(subjects) {
  const bySemester = new Map();
  for (const subject of subjects) {
    if (!bySemester.has(subject.semester)) {
      bySemester.set(subject.semester, []);
    }
    bySemester.get(subject.semester).push(subject);
  }
  return [...bySemester].map(([semester, list]) =>
    h(
      "section.stack.reveal",
      { "aria-label": "Semester " + semester },
      h("h2.folder-group__title", "Semester " + semester),
      h(
        "div.grid.grid--3",
        list.map((subject) => {
          const card = h(
            "button.card.card--choice.card--lift.folder",
            { type: "button" },
            h("div.line", h("span.badge-icon", icon("book-open")), h("span.tag.mono", subject.subjectCode)),
            h("h3.subject__name", subject.subjectName),
            h("p.row__meta.num", subject.resources === 1 ? "1 resource" : subject.resources + " resources"),
            h("span.more", "Open", icon("chevron-right")),
          );
          card.addEventListener("click", () => openSubject(subject.subjectId));
          return card;
        }),
      ),
    ),
  );
}

function openSubject(subjectId) {
  state.subjectId = String(subjectId);
  state.unitId = "";
  subjectField.select.value = state.subjectId;
  fillUnits();
  refresh();
  list.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** One subject's resources, grouped: whole-subject material first, then unit by unit. */
function subjectView(page) {
  const first = page.content[0];
  const back = h("button.button.button--quiet.button--small", { type: "button" }, icon("arrow-left"), "All subjects");
  back.addEventListener("click", clearFilters);
  const groups = new Map();
  for (const resource of page.content) {
    const key = resource.unitNo ?? 0;
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key).push(resource);
  }
  const ordered = [...groups].sort(([a], [b]) => a - b);
  return [
    h(
      "div.line.reveal",
      h("div.stack.stack--tight", h("p.eyebrow", first ? first.subjectCode : ""), h("h2", first ? first.subjectName : "Subject"), h("p.row__meta.num", page.totalElements === 1 ? "1 resource" : page.totalElements + " resources")),
      back,
    ),
    ...ordered.map(([unitNo, resources]) =>
      h(
        "section.stack.reveal",
        { "aria-label": unitNo ? "Unit " + unitNo : "Whole subject" },
        h("h3.folder-group__title", unitNo ? `Unit ${unitNo}${resources[0].unitTitle && resources[0].unitTitle !== "Unit " + unitNo ? ": " + resources[0].unitTitle : ""}` : "Whole subject"),
        h("div.grid.grid--3", stagger(resources.map((resource) => resourceCard(resource)))),
      ),
    ),
  ];
}

function load() {
  const mine = ++latest;
  if (!filtered()) {
    count.textContent = "";
    loadInto(list, () => api.get("/resources/subjects"), (subjects) => {
      if (mine !== latest) {
        return;
      }
      fill(list, subjects.length ? folders(subjects) : nothingFound());
      reveal();
    });
    return;
  }
  const oneSubject = state.subjectId && state.query.length < 2 && !state.type && !state.unitId;
  const size = oneSubject ? 100 : PAGE_SIZE;
  loadInto(list, () => api.get("/resources?" + query(size)), (page) => {
    if (mine !== latest) {
      return;
    }
    count.textContent = page.totalElements === 1 ? "1 resource" : page.totalElements + " resources";
    if (oneSubject && page.content.length) {
      fill(list, subjectView(page));
      reveal();
      return;
    }
    fill(
      list,
      page.content.length
        ? [h("div.grid.grid--3", stagger(page.content.map((resource) => resourceCard(resource)))), page.totalPages > 1 ? pager(page) : null]
        : nothingFound(),
    );
    reveal();
  });
}

function refresh() {
  state.page = 0;
  load();
}

async function fillUnits() {
  fill(unitField.select, option("", "All units"));
  unitField.select.disabled = !state.subjectId;
  if (!state.subjectId) {
    return;
  }
  try {
    const units = await api.get(`/subjects/${state.subjectId}/units`);
    fill(unitField.select, option("", "All units"), units.map((unit) => option(unit.id, `Unit ${unit.unitNo}: ${unit.title}`)));
  } catch {
    // The page still works without the unit filter.
    unitField.select.disabled = true;
  }
}

function clearFilters() {
  Object.assign(state, { query: "", subjectId: "", unitId: "", type: "", page: 0 });
  search.value = "";
  subjectField.select.value = "";
  typeField.select.value = "";
  fillUnits();
  load();
}

let waiting = 0;
search.addEventListener("input", () => {
  state.query = search.value.trim();
  clearTimeout(waiting);
  waiting = setTimeout(refresh, SEARCH_DELAY);
});
subjectField.select.addEventListener("change", () => {
  state.subjectId = subjectField.select.value;
  state.unitId = "";
  fillUnits();
  refresh();
});
unitField.select.addEventListener("change", () => {
  state.unitId = unitField.select.value;
  refresh();
});
typeField.select.addEventListener("change", () => {
  state.type = typeField.select.value;
  refresh();
});
reset.addEventListener("click", clearFilters);

fill(
  document.getElementById("content"),
  h(
    "section.card.reveal",
    { "aria-label": "Search and filters" },
    h("label.search.search--large", { for: "resource-search" }, h("span.visually-hidden", "Search resources"), icon("search"), search),
    h("div.filters", subjectField.element, unitField.element, typeField.element, reset, count),
  ),
  list,
);
fillUnits();
reveal();
load();

// The subject filter lists the subjects of the student's own branch. Without a filter, the page
// shows the subject folders.
api
  .get("/subjects")
  .then((subjects) => {
    fill(subjectField.select, option("", "All subjects"), subjects.map((subject) => option(subject.id, `${subject.code} · ${subject.name}`)));
  })
  .catch(() => {
    subjectField.select.disabled = true;
  });
