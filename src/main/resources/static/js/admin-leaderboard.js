import { dataTable, field, hidePageError, input, pager, rowAction, select, semesterOptions } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, formatDate, h, icon, tag } from "./dom.js";
import { count, platformTags } from "./coding-parts.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: the coding leaderboard (FR-COD-005, ADR 0017). Every student with a linked account, shown
// on the students' leaderboard or not, ranked by problems solved within the filter, with how many
// they solved in the last 30 days. One student's portfolio opens read-only on its own page.

const filters = { branch: "", semester: "", section: "", q: "", page: 0 };
const content = document.getElementById("content");
let searchTimer = 0;

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ size: 20, page: filters.page });
  for (const name of ["branch", "semester", "section", "q"]) {
    if (filters[name]) {
      params.set(name, filters[name]);
    }
  }
  loadInto(list, () => api.get("/admin/coding/leaderboard?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 student" : page.totalElements + " students"),
      dataTable(
        [
          { label: "Rank", cell: (row) => h("strong.num", "#" + row.rank) },
          { label: "Student", cell: (row) => h("div.stack.stack--tight", h("strong", row.fullName), h("span.row__meta", row.rollNo)) },
          { label: "Class", cell: (row) => `${row.branch} · ${row.semester} · ${row.section}` },
          { label: "Solved", align: "end", cell: (row) => h("strong.num", count(row.totalSolved)) },
          { label: "Last 30 days", align: "end", cell: (row) => (row.solvedLast30Days ? h("span.num", "+" + count(row.solvedLast30Days)) : h("span.muted", "0")) },
          { label: "Sites", cell: (row) => h("div.stack.stack--tight", platformTags(row.platforms), row.pendingAccounts ? h("span.row__meta", `${row.pendingAccounts} waiting for the code`) : null) },
          { label: "On board", cell: (row) => (row.showOnLeaderboard ? tag("Shown", "success") : tag("Hidden")) },
          { label: "Updated", cell: (row) => (row.lastSyncedAt ? formatDate(row.lastSyncedAt) : "—") },
          { label: "Actions", align: "end", cell: (row) => h("span.row-actions", rowAction("Open the portfolio of " + row.fullName, "eye", () => window.location.assign("/admin/portfolio.html?student=" + row.studentId))) },
        ],
        page.content,
        "No student has linked a coding account yet.",
      ),
      pager(page, (target) => {
        filters.page = target;
        load();
      }),
    );
  });
}

function filterBar() {
  const branch = input("branch", { maxlength: 20, placeholder: "Any", autocomplete: "off" });
  const semester = select("semester", semesterOptions("Any semester"), "");
  const section = input("section", { maxlength: 5, placeholder: "Any", autocomplete: "off" });
  const search = input("q", { type: "search", maxlength: 100, placeholder: "Name or roll number", autocomplete: "off" });
  const changed = (name, value) => {
    filters[name] = value.trim();
    filters.page = 0;
    load();
  };
  semester.addEventListener("change", () => changed("semester", semester.value));
  for (const [name, element] of [["branch", branch], ["section", section], ["q", search]]) {
    element.addEventListener("input", () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => changed(name, element.value), 300);
    });
  }
  return h(
    "section.card",
    { "aria-label": "Filters" },
    h("div.line.line--start.line--wrap", field("Search", search), field("Branch", branch), field("Semester", semester), field("Section", section)),
    h("p.row__meta", icon("info"), " Numbers are read from each student's public profiles every night. Only verified accounts count."),
  );
}

fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Students" }, h("div.skeleton")));
load();
