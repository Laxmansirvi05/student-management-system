import { api } from "./api.js";
import { fill, formatDate, h, icon, stagger } from "./dom.js";
import { reveal, swap } from "./motion.js";
import { categoryLabels, categoryTag, dueTag, dueText, empty, isWide, loadInto, openSheet } from "./parts.js";
import "./shell.js";

// Notices (FR-NOT-001): published notices, newest first, with a filter by category, a search box
// and the full text of the chosen notice. The server sends only notices this student may see.

const PAGE_SIZE = 100;
const content = document.getElementById("content");
let notices = [];
let category = "ALL";
let query = "";
let chosen = null;

function matches(notice) {
  const text = (notice.title + " " + notice.body).toLowerCase();
  return (category === "ALL" || notice.category === category) && text.includes(query.toLowerCase());
}

function detail(notice) {
  return [
    h("div.line.line--start.line--wrap", categoryTag(notice.category), dueTag(notice.dueDate)),
    h("h2.notice__title", notice.title),
    notice.dueDate
      ? h("div.inset.inset--row", h("span.line.line--start", icon("calendar"), h("strong.num", "Due " + formatDate(notice.dueDate))), h("span.eyebrow", dueText(notice.dueDate)))
      : null,
    h("div.notice__body", notice.body.split(/\n\s*\n/).map((paragraph) => h("p", paragraph))),
    h("p.note", icon("clock"), "Published " + formatDate(notice.publishedAt)),
  ];
}

function card(notice) {
  const element = h(
    "button.card.card--choice.reveal",
    { type: "button", "aria-pressed": String(notice === chosen && isWide()) },
    h("div.line", h("span.line.line--start", categoryTag(notice.category), h("span.eyebrow.num", formatDate(notice.publishedAt))), dueTag(notice.dueDate)),
    h("h3.subject__name", notice.title),
    h("p.row__meta.clamp-2", notice.body.split(/\n\s*\n/)[0]),
    notice.dueDate ? h("p.row__meta.line.line--start", icon("clock"), dueText(notice.dueDate)) : null,
  );
  element.addEventListener("click", () => {
    chosen = notice;
    if (isWide()) {
      for (const other of document.querySelectorAll("#list .card--choice")) {
        other.setAttribute("aria-pressed", String(other === element));
      }
      const panel = document.getElementById("detail");
      swap(panel, () => fill(panel, detail(notice)));
    } else {
      openSheet(detail(notice));
    }
  });
  return element;
}

function renderList() {
  const list = notices.filter(matches);
  fill(
    document.getElementById("list"),
    list.length ? stagger(list.map(card)) : h("div.card", empty("bell", "No notices match", "Try another category or search.")),
  );
}

function toolbar() {
  const options = [["ALL", "All"], ...Object.entries(categoryLabels)];
  const pills = h(
    "div.pills",
    { role: "group", "aria-label": "Category" },
    options.map(([value, label]) => {
      const count = notices.filter((notice) => value === "ALL" || notice.category === value).length;
      const pill = h("button.pill", { type: "button", "aria-pressed": String(value === category) }, label, h("span.pill__count", count));
      pill.addEventListener("click", () => {
        category = value;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(document.getElementById("list"), renderList);
      });
      return pill;
    }),
  );
  const input = h("input.search__input", { type: "search", id: "notice-search", placeholder: "Search notices", autocomplete: "off" });
  input.addEventListener("input", () => {
    query = input.value.trim();
    renderList();
    reveal();
  });
  return h("div.toolbar.reveal", pills, h("label.search.toolbar__search", { for: "notice-search" }, h("span.visually-hidden", "Search notices"), icon("search"), input));
}

loadInto(content, () => api.get("/notices?size=" + PAGE_SIZE), (page) => {
  notices = page.content;
  if (!notices.length) {
    fill(content, h("div.card", empty("bell", "No notices yet", "Announcements from the college appear here.")));
    return;
  }
  const wanted = new URLSearchParams(location.search).get("id");
  chosen = notices.find((notice) => String(notice.id) === wanted) || notices[0];
  fill(
    content,
    toolbar(),
    h("div.grid.grid--split", h("div.stack.stack--loose", { id: "list" }), h("aside.card.sticky.reveal.only-wide", { id: "detail", "aria-live": "polite" }, detail(chosen))),
  );
  renderList();
  reveal();
  // Opened from the dashboard on a phone: show the notice that was asked for.
  if (wanted && !isWide() && String(chosen.id) === wanted) {
    openSheet(detail(chosen));
  }
});
