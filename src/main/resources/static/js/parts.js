// Pieces of page that several student pages share: card headings, empty states, the chips for
// notice categories and due dates, the resource card, and the phone sheet for details.

import { messageFor } from "./api.js";
import { daysUntil, fill, formatDate, formatSize, h, icon, tag, toast } from "./dom.js";

/** How each kind of resource is named and coloured. */
export const resourceTypes = {
  NOTES: { label: "Notes", kind: "primary" },
  SYLLABUS: { label: "Syllabus", kind: "teal" },
  LAB_MANUAL: { label: "Lab manual", kind: "purple" },
  QUESTION_PAPER: { label: "Question paper", kind: "danger" },
  MODEL_PAPER: { label: "Model paper", kind: "orange" },
  IMPORTANT_QUESTIONS: { label: "Important questions", kind: "success" },
};

/**
 * Loads data and draws it. While the request runs, the grey shapes already in the page stay in
 * place. If it fails, the container shows what went wrong and a Retry button.
 */
export async function loadInto(container, request, render) {
  try {
    render(await request());
  } catch (failure) {
    const retry = h("button.button.button--secondary.button--small", { type: "button" }, icon("refresh"), "Retry");
    retry.addEventListener("click", () => {
      fill(container, h("div.skeleton.skeleton--card"));
      loadInto(container, request, render);
    });
    fill(
      container,
      h(
        "div.card",
        { role: "alert" },
        h("div.line.line--start", h("span.badge-icon.badge-icon--round.badge-icon--danger", icon("alert")), h("div.stack.stack--tight", h("strong", "This could not be loaded"), h("span.row__meta", messageFor(failure)))),
        retry,
      ),
    );
  }
}

/** The heading row of a card: an icon, the title, and a "View all" link when href is given. */
export function cardHead(iconName, title, href = "", linkText = "View all") {
  return h(
    "div.card__head",
    h("h2.card__title", icon(iconName), title),
    href ? h("a.more", { href }, linkText, icon("chevron-right")) : null,
  );
}

/** What a list shows when it has nothing: an icon, a short title, a sentence and one action. */
export function empty(iconName, title, text, action = null) {
  return h(
    "div.empty",
    h("span.badge-icon.badge-icon--round.badge-icon--neutral", icon(iconName)),
    h("h3", title),
    text ? h("p", text) : null,
    action,
  );
}

const CATEGORIES = {
  GENERAL: { label: "General", kind: "" },
  EXAM: { label: "Exam", kind: "purple" },
  ASSIGNMENT: { label: "Assignment", kind: "primary" },
  EVENT: { label: "Event", kind: "teal" },
};

export const categoryLabels = Object.fromEntries(
  Object.entries(CATEGORIES).map(([key, value]) => [key, value.label]),
);

export function categoryTag(category) {
  const entry = CATEGORIES[category] || CATEGORIES.GENERAL;
  return tag(entry.label, entry.kind);
}

/** "Due today", "Due tomorrow", "Due in 3 days" or "Was due 2 days ago". */
export function dueText(dueDate) {
  const days = daysUntil(dueDate);
  if (days === 0) {
    return "Due today";
  }
  if (days === 1) {
    return "Due tomorrow";
  }
  if (days > 1) {
    return `Due in ${days} days`;
  }
  return days === -1 ? "Was due yesterday" : `Was due ${-days} days ago`;
}

/** The chip for a deadline: amber when it is within three days, grey when it has passed. */
export function dueTag(dueDate) {
  if (!dueDate) {
    return null;
  }
  const days = daysUntil(dueDate);
  if (days < 0) {
    return tag("Past due", "", "history");
  }
  if (days <= 3) {
    return tag("Due soon", "warning", "clock");
  }
  return tag("Due " + formatDate(dueDate), "", "calendar");
}

/** A score out of a total as a coloured chip: green from 75%, red below 50%. */
export function scoreKind(percentage) {
  if (percentage >= 75) {
    return "success";
  }
  return percentage < 50 ? "danger" : "";
}

// ---- Bookmarks ----
// Until the bookmark API exists, the choice is kept for this browser tab only.
const BOOKMARK_KEY = "sample-bookmarks";

function readBookmarks(resources) {
  const saved = sessionStorage.getItem(BOOKMARK_KEY);
  if (saved) {
    return new Set(saved.split(",").filter(Boolean).map(Number));
  }
  return new Set(resources.filter((resource) => resource.bookmarked).map((resource) => resource.id));
}

export function bookmarkStore(resources) {
  const ids = readBookmarks(resources);
  return {
    has: (id) => ids.has(id),
    toggle(id) {
      if (ids.has(id)) {
        ids.delete(id);
      } else {
        ids.add(id);
      }
      sessionStorage.setItem(BOOKMARK_KEY, [...ids].join(","));
      return ids.has(id);
    },
  };
}

/** The round bookmark button of a resource. onChange is told the new state. */
export function bookmarkButton(resource, store, onChange = () => {}) {
  const button = h(
    "button.icon-button.icon-button--bare",
    { type: "button", "aria-pressed": String(store.has(resource.id)) },
    icon("bookmark"),
  );
  const label = () =>
    button.setAttribute(
      "aria-label",
      (store.has(resource.id) ? "Remove bookmark: " : "Bookmark: ") + resource.title,
    );
  label();
  button.addEventListener("click", () => {
    const saved = store.toggle(resource.id);
    button.setAttribute("aria-pressed", String(saved));
    label();
    toast(saved ? "Saved to bookmarks" : "Removed from bookmarks");
    onChange(saved);
  });
  return button;
}

export function typeTag(resourceType) {
  const entry = resourceTypes[resourceType];
  return tag(entry.label, entry.kind);
}

export function resourcePlace(resource) {
  return resource.unitNo ? `${resource.subjectName} · Unit ${resource.unitNo}` : resource.subjectName;
}

/** One resource in a grid: type, title, subject and unit, size and date, bookmark and open. */
export function resourceCard(resource, store, onBookmarkChange) {
  const href = "/student/resource.html?id=" + resource.id;
  return h(
    "article.card.card--lift.reveal.resource",
    h(
      "div.line",
      h("div.line.line--start", h("span.badge-icon.badge-icon--danger", icon("file")), typeTag(resource.resourceType)),
      bookmarkButton(resource, store, onBookmarkChange),
    ),
    h("div.stack", h("h3.resource__title", h("a", { href }, resource.title)), h("p.row__meta", resourcePlace(resource))),
    h(
      "div.card__foot",
      h("span.num", `${formatSize(resource.fileSizeBytes)} · ${formatDate(resource.createdAt)}`),
      h("a.icon-button", { href, "aria-label": "Open " + resource.title }, icon("arrow-right")),
    ),
  );
}

// ---- Details on phones ----
const wide = window.matchMedia("(min-width: 1100px)");

/** True when there is room for a list and its detail panel side by side. */
export function isWide() {
  return wide.matches;
}

const sheet = document.getElementById("detail-sheet");
sheet?.addEventListener("click", (event) => {
  if (event.target === sheet || event.target.closest("[data-close]")) {
    sheet.close();
  }
});

/** Shows details in the sheet that slides up from the bottom, on screens with no side panel. */
export function openSheet(...children) {
  fill(document.getElementById("detail-sheet-body"), ...children);
  sheet.showModal();
}
