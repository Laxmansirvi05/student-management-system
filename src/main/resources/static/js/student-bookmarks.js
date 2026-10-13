import { api } from "./api.js";
import { fill, h, stagger } from "./dom.js";
import { leave, reveal } from "./motion.js";
import { empty, loadInto, resourceCard } from "./parts.js";
import "./shell.js";

// Bookmarks (FR-LIB-004): the resources the student saved. Removing one takes it off the page.
// A resource that was hidden or deleted is simply no longer in the list the server sends.

const PAGE_SIZE = 100;
const content = document.getElementById("content");
let shown = 0;

function countText(number) {
  return number === 1 ? "1 saved resource" : number + " saved resources";
}

function emptyState() {
  const browse = h("a.button.button--primary.button--small", { href: "/student/resources.html" }, "Browse resources");
  return h("div.card", empty("bookmark", "No bookmarks yet", "Save a resource to find it here later.", browse));
}

/** Removes a card. When it was the last one, the empty state appears. */
function remove(card) {
  leave(card, () => {
    card.remove();
    shown -= 1;
    if (shown > 0) {
      document.getElementById("count").textContent = countText(shown);
    } else {
      fill(content, emptyState());
    }
  });
}

function render(page) {
  shown = page.totalElements;
  if (!page.content.length) {
    fill(content, emptyState());
    return;
  }
  const cards = page.content.map((resource) => {
    const card = resourceCard(resource, (saved) => {
      if (!saved) {
        remove(card);
      }
    });
    return card;
  });
  fill(content, h("p.row__meta.num", { id: "count" }, countText(shown)), h("div.grid.grid--3", stagger(cards)));
  reveal();
}

loadInto(content, () => api.get("/bookmarks/me?size=" + PAGE_SIZE), render);
