import { api } from "./api.js";
import { fill, h, icon } from "./dom.js";
import { renderDashboard } from "./coding-dashboard.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: one student's coding portfolio, read-only (FR-COD-005). The same dashboard the student
// sees, without the controls that change anything and without verification codes.

const content = document.getElementById("content");
const studentId = new URLSearchParams(window.location.search).get("student");

function shownLine(data) {
  return h("p.cd-controls.row__meta", icon(data.showOnLeaderboard ? "eye" : "lock"), data.showOnLeaderboard ? " Shows on the students' leaderboard" : " Hidden from the students' leaderboard");
}

if (!studentId || !/^\d{1,18}$/.test(studentId)) {
  fill(content, h("div.card", h("p", "Choose a student on the coding leaderboard.")));
} else {
  loadInto(content, () => api.get(`/admin/coding/students/${studentId}/portfolio`), (data) => {
    document.getElementById("page-title").textContent = data.fullName;
    renderDashboard(content, data.portfolio, { headerControls: shownLine });
  });
}
