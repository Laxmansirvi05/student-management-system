import { pager } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, h, icon, tag } from "./dom.js";
import { count, platformTags } from "./coding-parts.js";
import { reveal } from "./motion.js";
import { cardHead, empty, loadInto } from "./parts.js";
import "./shell.js";

// The students' leaderboard (FR-COD-004): the students who chose to show, from the whole college,
// ranked by problems solved on their verified accounts. Equal totals share a rank. The student's
// own place is shown above the list, and how to join when they are not on it.

const PAGE_SIZE = 20;
const content = document.getElementById("content");
let pageNumber = 0;

function standing(me) {
  if (!me.verifiedAccounts) {
    return h("section.card", empty("code", "You are not on the leaderboard yet", "Link and verify a coding account on your portfolio first.", h("a.button.button--primary", { href: "/student/portfolio.html" }, icon("plus"), "Open your portfolio")));
  }
  if (!me.showOnLeaderboard) {
    return h(
      "section.card.inset--row",
      h("div.stack.stack--tight", h("strong", `You have solved ${count(me.totalSolved)} problems.`), h("span.row__meta", "Switch on “Show me on the leaderboard” on your portfolio to take your place.")),
      h("a.button.button--secondary", { href: "/student/portfolio.html" }, "Open your portfolio"),
    );
  }
  return h(
    "div.stats",
    h("div.stat.reveal", h("p.stat__value.num", "#" + me.rank), h("p.stat__label", "Your rank")),
    h("div.stat.reveal", h("p.stat__value.num", count(me.totalSolved)), h("p.stat__label", "Problems solved")),
  );
}

function entryRow(entry) {
  return h(
    "li.row.coding-rank" + (entry.me ? ".coding-rank--me" : ""),
    h("span.coding-rank__place.num", "#" + entry.rank),
    h(
      "div.stack.stack--tight.coding-rank__who",
      h("span.line.line--start", h("span.row__title", entry.fullName), entry.me ? tag("You", "primary") : null),
      h("span.row__meta", `${entry.branch} · Semester ${entry.semester}`),
      platformTags(entry.platforms),
    ),
    h("span.coding-rank__total", h("strong.num", count(entry.totalSolved)), h("span.row__meta", "solved")),
  );
}

function board(page) {
  return h(
    "section.card",
    cardHead("target", "Leaderboard"),
    page.content.length ? h("ul.rows", page.content.map(entryRow)) : empty("users", "Nobody here yet", "Students who switch on “Show me on the leaderboard” appear here."),
    pager(page, (target) => {
      pageNumber = target;
      load();
    }),
  );
}

function load() {
  loadInto(content, () => api.get(`/coding/leaderboard?page=${pageNumber}&size=${PAGE_SIZE}`), (page) => {
    fill(content, standing(page.me), board(page));
    reveal();
  });
}

load();
