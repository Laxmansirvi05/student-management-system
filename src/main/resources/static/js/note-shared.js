import { messageFor } from "./api.js";
import { fill, formatDate, h, icon } from "./dom.js";
import { empty } from "./parts.js";
import { openSharedNote } from "./notes/notes-api.js";
import { setHtml } from "./notes/safe-html.js";
import "./shell.js";

// A note opened through its share link. It is read-only, and only for a signed-in student: the
// server answers "not found" for a link that is off, unknown, or whose note is in the Trash.

const content = document.getElementById("content");
const slug = new URLSearchParams(location.search).get("slug");

function showMissing() {
  fill(
    content,
    h("div.card", empty("lock", "This note is not available", "The link may have been turned off, or the note may have been deleted. Ask the student who shared it for a new link.", h("a.button.button--secondary", { href: "/student/notes.html" }, icon("arrow-left"), "Back to notes"))),
  );
}

// The server opens a picture or file of someone else's note only for a reader who holds the share
// link, so every attachment address in the note carries this page's slug.
const ATTACHMENT = /^\/api\/v1\/notes\/attachments\/\d+\/file$/;

function addShareToAttachments(root) {
  for (const element of root.querySelectorAll("img[src], a[href]")) {
    const attribute = element.tagName === "IMG" ? "src" : "href";
    const address = new URL(element.getAttribute(attribute), location.origin);
    if (address.origin === location.origin && ATTACHMENT.test(address.pathname)) {
      address.searchParams.set("share", slug);
      element.setAttribute(attribute, address.pathname + address.search);
    }
  }
}

async function show() {
  if (!slug) {
    showMissing();
    return;
  }
  try {
    const note = await openSharedNote(slug);
    const body = h("div.note-document");
    setHtml(body, note.content);
    addShareToAttachments(body);
    fill(
      content,
      h(
        "article.card.nt-shared",
        h("h2", note.title || "Untitled"),
        h("p.nt-shared__meta", `Shared by ${note.sharedBy || "a classmate"} · Last updated ${formatDate(note.updatedAt)} · View only`),
        body,
      ),
    );
  } catch (failure) {
    if (failure.status === 404) {
      showMissing();
    } else {
      fill(content, h("div.card", { role: "alert" }, messageFor(failure)));
    }
  }
}

show();
