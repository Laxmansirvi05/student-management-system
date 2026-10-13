import { fill, formatDate, formatSize, h, icon, toast } from "./dom.js";
import { reveal } from "./motion.js";
import { bookmarkStore, empty, resourcePlace, typeTag } from "./parts.js";
import { resources } from "./sample-data.js";
import "./shell.js";

// One resource (FR-LIB-003): its details, the actions, and the PDF preview. The real page shows
// the file in a frame through a short-lived signed link; the sample entries have no file yet.

const id = Number(new URLSearchParams(location.search).get("id"));
const resource = resources.find((entry) => entry.id === id);
const content = document.getElementById("content");

function notYet() {
  toast("This is a sample entry, so there is no file yet");
}

function details() {
  const store = bookmarkStore(resources);
  const saved = () => store.has(resource.id);
  const bookmark = h("button.button.button--secondary", { type: "button", "aria-pressed": String(saved()) }, icon("bookmark"), h("span", saved() ? "Saved" : "Bookmark"));
  bookmark.addEventListener("click", () => {
    const now = store.toggle(resource.id);
    bookmark.setAttribute("aria-pressed", String(now));
    bookmark.lastChild.textContent = now ? "Saved" : "Bookmark";
    toast(now ? "Saved to bookmarks" : "Removed from bookmarks");
  });
  const download = h("button.button.button--primary", { type: "button" }, icon("download"), `Download (${formatSize(resource.fileSizeBytes)})`);
  const open = h("button.button.button--secondary", { type: "button" }, icon("external"), "Open in new tab");
  download.addEventListener("click", notYet);
  open.addEventListener("click", notYet);
  return h(
    "section.card.reveal.resource-head",
    h(
      "div.stack",
      h("div.line.line--start.line--wrap", h("span.tag.mono", resource.subjectCode), resource.unitNo ? h("span.tag", "Unit " + resource.unitNo) : null, typeTag(resource.resourceType)),
      h("h1.resource-head__title", resource.title),
      h("p.row__meta", resourcePlace(resource) + (resource.unitTitle ? ": " + resource.unitTitle : "")),
      h("p.row__meta.num", `${formatSize(resource.fileSizeBytes)} · Uploaded ${formatDate(resource.createdAt)}`),
    ),
    h("div.resource-head__actions", download, open, bookmark),
  );
}

function preview() {
  const lines = [92, 78, 85, 60, 0, 88, 94, 72, 81, 55, 0, 90, 68, 84].map((width) =>
    width ? h("span.paper__line", { style: { "--w": width + "%" } }) : h("span.paper__gap"),
  );
  return h(
    "section.card.viewer.reveal",
    { "aria-label": "Preview" },
    h("div.viewer__bar", h("span.line.line--start", icon("file"), h("span.mono.clip", resource.originalFileName)), h("span.tag", "PDF")),
    h(
      "div.viewer__stage",
      h("div.paper", { "aria-hidden": "true" }, h("span.paper__title"), lines),
      h(
        "div.viewer__note",
        h("span.badge-icon.badge-icon--round", icon("file")),
        h("h2", "The preview appears here"),
        h("p", "This is a sample entry, so there is no file to show yet. Once resources are uploaded, the PDF opens in this space."),
      ),
    ),
  );
}

if (resource) {
  document.title = resource.title + " · Campus Companion";
  fill(content, details(), preview());
} else {
  const back = h("a.button.button--primary.button--small", { href: "/student/resources.html" }, "Browse resources");
  fill(content, h("div.card", empty("search", "We could not find that resource", "It may have been removed.", back)));
}
reveal();
