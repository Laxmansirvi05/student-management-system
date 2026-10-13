import { api, messageFor } from "./api.js";
import { showBanner } from "./forms.js";
import "./shell.js";

// The student's own profile (FR-STU-002). Everything here is read-only.
const loading = document.getElementById("loading");
const error = document.getElementById("error");
const banner = document.getElementById("banner");
const profile = document.getElementById("profile");

function show(id, text) {
  document.getElementById(id).textContent = text;
}

async function load() {
  loading.hidden = false;
  error.hidden = true;
  profile.hidden = true;
  try {
    const student = await api.get("/students/me");
    show("full-name", student.fullName);
    show("roll-no", student.rollNo);
    show("email", student.email);
    show("branch", student.branch);
    show("semester", String(student.currentSemester));
    show("section", student.section);
    show("admission-year", String(student.admissionYear));
    show("phone", student.phone || "Not provided");
    profile.hidden = false;
  } catch (failure) {
    showBanner(banner, "error", messageFor(failure));
    error.hidden = false;
  } finally {
    loading.hidden = true;
  }
}

document.getElementById("retry").addEventListener("click", load);
load();
