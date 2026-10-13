import { fill, h, icon, stagger } from "./dom.js";
import { reveal, swap } from "./motion.js";
import { bookmarkStore, empty, resourceCard } from "./parts.js";
import { resources, resourceTypes, subjects } from "./sample-data.js";
import "./shell.js";

// The resource hub (FR-LIB-001, FR-LIB-002): search, three filters, and pages of resource cards.
// Students can read and bookmark; uploading belongs to the admin side.

const PAGE_SIZE = 6;
const store = bookmarkStore(resources);
const state = { query: "", subject: "", unit: "", type: "", page: 1 };

function matches(resource) {
  const text = (resource.title + " " + resource.subjectName + " " + (resource.unitTitle || "")).toLowerCase();
  return (
    text.includes(state.query.toLowerCase()) &&
    (!state.subject || resource.subjectCode === state.subject) &&
    (!state.unit || String(resource.unitNo) === state.unit) &&
    (!state.type || resource.resourceType === state.type)
  );
}

function option(value, label) {
  return h("option", { value }, label);
}

function field(id, label, options) {
  const select = h("select.select", { id }, options);
  return { select, element: h("label.filter", { for: id }, h("span.visually-hidden", label), select) };
}

const search = h("input.search__input", { type: "search", id: "resource-search", placeholder: "Search notes, papers and manuals", autocomplete: "off" });
const subjectField = field("filter-subject", "Subject", [option("", "All subjects"), ...subjects.map((subject) => option(subject.code, subject.name))]);
const unitField = field("filter-unit", "Unit", [option("", "All units")]);
const typeField = field("filter-type", "Type", [option("", "All types"), ...Object.entries(resourceTypes).map(([value, entry]) => option(value, entry.label))]);
const count = h("p.row__meta.num", { "aria-live": "polite" });
const reset = h("button.button.button--quiet.button--small", { type: "button" }, icon("refresh"), "Clear filters");

function fillUnits() {
  const units = [...new Set(resources.filter((resource) => resource.subjectCode === state.subject && resource.unitNo).map((resource) => resource.unitNo))].sort();
  fill(unitField.select, option("", "All units"), units.map((unit) => option(String(unit), "Unit " + unit)));
  unitField.select.disabled = !state.subject;
}

function pager(pages) {
  const button = (label, iconName, target, before) => {
    const element = h("button.button.button--secondary.button--small", { type: "button", disabled: target < 1 || target > pages }, before ? icon(iconName) : null, label, before ? null : icon(iconName));
    element.addEventListener("click", () => {
      state.page = target;
      swap(document.getElementById("list"), renderList);
      document.getElementById("list").scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return element;
  };
  return h(
    "nav.pager",
    { "aria-label": "Pages" },
    button("Previous", "arrow-left", state.page - 1, true),
    h("span.row__meta.num", "Page ", h("strong", state.page), " of " + pages),
    button("Next", "arrow-right", state.page + 1, false),
  );
}

function renderList() {
  const found = resources.filter(matches);
  const pages = Math.max(1, Math.ceil(found.length / PAGE_SIZE));
  state.page = Math.min(state.page, pages);
  const shown = found.slice((state.page - 1) * PAGE_SIZE, state.page * PAGE_SIZE);
  count.textContent = found.length === 1 ? "1 resource" : found.length + " resources";
  const clear = h("button.button.button--primary.button--small", { type: "button" }, "Clear filters");
  clear.addEventListener("click", clearFilters);
  fill(
    document.getElementById("list"),
    shown.length
      ? [h("div.grid.grid--3", stagger(shown.map((resource) => resourceCard(resource, store)))), pages > 1 ? pager(pages) : null]
      : h("div.card", empty("search", "No resources match your filters", "Try a different search or fewer filters.", clear)),
  );
}

function refresh() {
  state.page = 1;
  swap(document.getElementById("list"), renderList);
}

function clearFilters() {
  Object.assign(state, { query: "", subject: "", unit: "", type: "", page: 1 });
  search.value = "";
  subjectField.select.value = "";
  typeField.select.value = "";
  fillUnits();
  refresh();
}

search.addEventListener("input", () => {
  state.query = search.value.trim();
  state.page = 1;
  renderList();
  reveal();
});
subjectField.select.addEventListener("change", () => {
  state.subject = subjectField.select.value;
  state.unit = "";
  fillUnits();
  refresh();
});
unitField.select.addEventListener("change", () => {
  state.unit = unitField.select.value;
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
  h("div.stack.stack--loose", { id: "list" }),
);
fillUnits();
renderList();
reveal();
