import { api, messageFor } from "./api.js";
import { showBanner } from "./forms.js";
import { countUp, enableTilt } from "./motion.js";
import { initialsOf } from "./shell.js";

// The student's own profile (FR-STU-002). Everything here is read-only.
const loading = document.getElementById("loading");
const error = document.getElementById("error");
const banner = document.getElementById("banner");
const profile = document.getElementById("profile");
const copyEmail = document.getElementById("copy-email");
const copyHint = document.getElementById("copy-hint");

function show(id, text) {
  document.getElementById(id).textContent = text;
}

function render(student) {
  show("initials", initialsOf(student.fullName));
  show("hero-name", student.fullName);
  show(
    "programme",
    `${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`,
  );
  show("roll-chip", student.rollNo);
  show("admitted-chip", String(student.admissionYear));
  show("email-chip", student.email);

  show("full-name", student.fullName);
  show("roll-no", student.rollNo);
  show("email", student.email);
  show("branch", student.branch);
  show("semester", String(student.currentSemester));
  show("section", student.section);
  show("admission-year", String(student.admissionYear));
  show("phone", student.phone || "Not provided");

  show("stat-section", student.section);
  profile.hidden = false;
  countUp(document.getElementById("stat-semester"), student.currentSemester);
  // Two semesters make one year of study.
  countUp(document.getElementById("stat-year"), Math.ceil(student.currentSemester / 2));
  countUp(document.getElementById("stat-admission"), student.admissionYear);
}

async function load() {
  loading.hidden = false;
  error.hidden = true;
  profile.hidden = true;
  try {
    render(await api.get("/students/me"));
  } catch (failure) {
    showBanner(banner, "error", messageFor(failure));
    error.hidden = false;
  } finally {
    loading.hidden = true;
  }
}

copyEmail.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(document.getElementById("email-chip").textContent);
    copyHint.textContent = "Copied";
  } catch {
    copyHint.textContent = "Could not copy";
  }
  setTimeout(() => {
    copyHint.textContent = "Copy";
  }, 1600);
});

document.getElementById("retry").addEventListener("click", load);
enableTilt();
load();
