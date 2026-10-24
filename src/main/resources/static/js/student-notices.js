import { api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, stagger } from "./dom.js";
import { reveal, swap } from "./motion.js";
import { categoryLabels, categoryTag, dueTag, dueText, empty, isWide, loadInto, openSheet } from "./parts.js";
import "./shell.js";

// Notices (FR-NOT-001, FR-NOT-002): published notices, newest first, with a filter by category, a
// search box and the chosen notice with its official document (the college's circular with its
// stamp and signature), shown through a short-lived link. The server sends only notices this
// student may see.

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

/** The official document: the picture itself, or the PDF in a frame, with open and download. */
function officialDocument(notice) {
  const holder = h("div.notice-doc", h("p.row__meta", "Loading the official notice…"));
  const isPicture = notice.documentType === "image/jpeg";
  api
    .get(`/notices/${notice.id}/document`)
    .then(async (link) => {
      const open = h("a.button.button--secondary.button--small", { href: link.url, target: "_blank", rel: "noopener noreferrer" }, icon("external"), "Open full size");
      const save = h("button.button.button--quiet.button--small", { type: "button" }, icon("download"), "Download");
      save.addEventListener("click", async () => {
        try {
          const file = await api.get(`/notices/${notice.id}/document?download=true`);
          const anchor = h("a", { href: file.url, download: "" });
          document.body.append(anchor);
          anchor.click();
          anchor.remove();
        } catch (failure) {
          holder.append(h("p.field__error", messageFor(failure)));
        }
      });
      let view;
      if (isPicture) {
        view = h("a.notice-doc__picture", { href: link.url, target: "_blank", rel: "noopener noreferrer", "aria-label": "Open the official notice full size" }, h("img", { src: link.url, alt: "Official notice: " + notice.title, loading: "lazy" }));
      } else if (navigator.pdfViewerEnabled === false) {
        view = h("p.row__meta", "Your browser cannot show PDFs inside the page. Use Open full size or Download.");
      } else {
        // Fit the page to the width and hide the thumbnails, so the circular is readable in the panel.
        view = h("iframe.notice-doc__frame", { src: link.url + "#navpanes=0&view=FitH", title: "Official notice: " + notice.title });
      }
      fill(holder, h("div.notice-doc__head", h("span.eyebrow", icon("shield"), " Official notice"), h("span.notice-doc__actions", open, save)), view);
    })
    .catch((failure) => fill(holder, h("p.row__meta", messageFor(failure))));
  return holder;
}

function detail(notice) {
  return [
    h("div.line.line--start.line--wrap", categoryTag(notice.category), dueTag(notice.dueDate)),
    h("h2.notice__title", notice.title),
    notice.dueDate
      ? h("div.inset.inset--row", h("span.line.line--start", icon("calendar"), h("strong.num", "Due " + formatDate(notice.dueDate))), h("span.eyebrow", dueText(notice.dueDate)))
      : null,
    h("div.notice__body", notice.body.split(/\n\s*\n/).map((paragraph) => h("p", paragraph))),
    notice.examPapers?.length
      ? h(
          "div.stack.stack--tight",
          h("p.eyebrow", "Exam papers"),
          h("ul.rows", notice.examPapers.map((paper) => h("li.row.results-row", h("span.row__meta.num", formatDate(paper.examDate)), h("span.row__title", paper.subject)))),
        )
      : null,
    notice.documentType ? officialDocument(notice) : null,
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
    h(
      "p.row__meta.line.line--start.line--wrap",
      notice.dueDate ? h("span.line.line--start", icon("clock"), dueText(notice.dueDate)) : null,
      notice.documentType ? h("span.line.line--start", icon("file"), "Official notice attached") : null,
    ),
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
