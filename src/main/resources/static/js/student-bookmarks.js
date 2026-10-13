import { fill, h, stagger } from "./dom.js";
import { leave, reveal } from "./motion.js";
import { bookmarkStore, empty, resourceCard } from "./parts.js";
import { resources } from "./sample-data.js";
import "./shell.js";

// Bookmarks (FR-LIB-004): the resources the student saved. Removing one takes it off the page.

const store = bookmarkStore(resources);
const content = document.getElementById("content");
const savedResources = () => resources.filter((resource) => store.has(resource.id));

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
    const left = savedResources().length;
    if (left) {
      document.getElementById("count").textContent = countText(left);
    } else {
      fill(content, emptyState());
    }
  });
}

function render() {
  const saved = savedResources();
  if (!saved.length) {
    fill(content, emptyState());
    return;
  }
  const cards = saved.map((resource) => {
    const card = resourceCard(resource, store, () => remove(card));
    return card;
  });
  fill(content, h("p.row__meta.num", { id: "count" }, countText(saved.length)), h("div.grid.grid--3", stagger(cards)));
  reveal();
}

render();
