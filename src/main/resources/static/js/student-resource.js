import { api, messageFor } from "./api.js";
import { fill, formatDate, formatSize, h, icon, toast } from "./dom.js";
import { reveal } from "./motion.js";
import { empty, examText, loadInto, resourcePlace, toggleBookmark, typeTag } from "./parts.js";
import "./shell.js";

// One resource (FR-LIB-003): its details, the actions, and the PDF shown in the page. The file is
// never served by this site. The server hands out a link that lasts five minutes, and only after
// it has checked that this user may read the resource.

const id = Number(new URLSearchParams(location.search).get("id"));
const content = document.getElementById("content");
const UNAVAILABLE = "This file is temporarily unavailable";

function linkFor(download) {
  return api.get(`/resources/${id}/download-url` + (download ? "?download=true" : ""));
}

/** Why a link could not be made, in words for the student. */
function reasonFor(failure) {
  return failure.code === "STORAGE_UNAVAILABLE" ? UNAVAILABLE + ". Please try again in a little while." : messageFor(failure);
}

function actions(resource) {
  const bookmark = h("button.button.button--secondary", { type: "button" }, icon("bookmark"), h("span"));
  const showBookmark = () => {
    bookmark.setAttribute("aria-pressed", String(resource.bookmarked));
    bookmark.lastChild.textContent = resource.bookmarked ? "Saved" : "Bookmark";
  };
  showBookmark();
  bookmark.addEventListener("click", async () => {
    bookmark.disabled = true;
    try {
      const saved = await toggleBookmark(resource);
      showBookmark();
      toast(saved ? "Saved to bookmarks" : "Removed from bookmarks");
    } catch (failure) {
      toast(messageFor(failure));
    } finally {
      bookmark.disabled = false;
    }
  });

  const download = h("button.button.button--primary", { type: "button" }, icon("download"), `Download (${formatSize(resource.fileSizeBytes)})`);
  download.addEventListener("click", async () => {
    download.disabled = true;
    try {
      // The link answers with "save this file", so the browser downloads it and stays on the page.
      window.location.assign((await linkFor(true)).url);
    } catch (failure) {
      toast(reasonFor(failure));
    } finally {
      download.disabled = false;
    }
  });

  const open = h("button.button.button--secondary", { type: "button" }, icon("external"), "Open in new tab");
  open.addEventListener("click", async () => {
    // The tab is opened at once, while the browser still counts it as the user's click. The link
    // is put into it when it arrives.
    const tab = window.open("", "_blank");
    try {
      const link = await linkFor(false);
      if (tab) {
        tab.opener = null;
        tab.location.replace(link.url);
      } else {
        window.location.assign(link.url);
      }
    } catch (failure) {
      tab?.close();
      toast(reasonFor(failure));
    }
  });
  return h("div.resource-head__actions", download, open, bookmark);
}

function details(resource) {
  return h(
    "section.card.reveal.resource-head",
    h(
      "div.stack",
      h("div.line.line--start.line--wrap", h("span.tag.mono", resource.subjectCode), resource.unitNo ? h("span.tag", "Unit " + resource.unitNo) : null, typeTag(resource.type)),
      h("h1.resource-head__title", resource.title),
      h("p.row__meta", resourcePlace(resource) + (resource.unitTitle ? ": " + resource.unitTitle : "")),
      resource.description ? h("p.resource-head__text", resource.description) : null,
      h("p.row__meta.num", [examText(resource), formatSize(resource.fileSizeBytes), "Uploaded " + formatDate(resource.createdAt)].filter(Boolean).join(" · ")),
    ),
    actions(resource),
  );
}

/** A grey page of lines, shown while the link is on its way and behind the "unavailable" note. */
function paper() {
  const lines = [92, 78, 85, 60, 0, 88, 94, 72, 81, 55, 0, 90, 68, 84].map((width) =>
    width ? h("span.paper__line", { style: { "--w": width + "%" } }) : h("span.paper__gap"),
  );
  return h("div.paper", { "aria-hidden": "true" }, h("span.paper__title"), lines);
}

function preview(resource) {
  const stage = h("div.viewer__stage", paper());

  async function show() {
    fill(stage, paper());
    try {
      const link = await linkFor(false);
      fill(stage, h("iframe.viewer__frame", { src: link.url, title: "Preview of " + resource.title, referrerpolicy: "no-referrer" }));
    } catch (failure) {
      const retry = h("button.button.button--secondary.button--small", { type: "button" }, icon("refresh"), "Try again");
      retry.addEventListener("click", show);
      fill(
        stage,
        paper(),
        h(
          "div.viewer__note",
          { role: "status" },
          h("span.badge-icon.badge-icon--round.badge-icon--neutral", icon("file")),
          h("h2", failure.code === "STORAGE_UNAVAILABLE" ? UNAVAILABLE : "The preview could not be shown"),
          h("p", failure.code === "STORAGE_UNAVAILABLE" ? "The rest of the page still works. Please try again in a little while." : messageFor(failure)),
          retry,
        ),
      );
    }
  }

  show();
  return h(
    "section.card.viewer.reveal",
    { "aria-label": "Preview" },
    h("div.viewer__bar", h("span.line.line--start", icon("file"), h("span.clip", resource.title)), h("span.tag", "PDF")),
    stage,
  );
}

function missing() {
  const back = h("a.button.button--primary.button--small", { href: "/student/resources.html" }, "Browse resources");
  fill(content, h("div.card", empty("search", "We could not find that resource", "It may have been removed.", back)));
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    missing();
    return;
  }
  let found = true;
  await loadInto(
    content,
    async () => {
      try {
        return await api.get("/resources/" + id);
      } catch (failure) {
        if (failure.status !== 404) {
          throw failure;
        }
        found = false;
        return null;
      }
    },
    (resource) => {
      if (!found) {
        missing();
        return;
      }
      document.title = resource.title + " · Campus Companion";
      fill(content, details(resource), preview(resource));
    },
  );
  reveal();
}

load();
