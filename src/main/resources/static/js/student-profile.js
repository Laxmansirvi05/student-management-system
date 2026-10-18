import { api, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { setFieldError, showBanner } from "./forms.js";

import { initialsOf } from "./shell.js";

// The student's own profile (FR-STU-002, FR-STU-003). What the college manages is read-only. The
// student can change their photo, a short text, skills, personal email, phone and three links.
// The server decides whose profile it is from the session; no ID is ever sent.

const MAX_PHOTO_BYTES = 2 * 1024 * 1024;
const loading = document.getElementById("loading");
const error = document.getElementById("error");
const banner = document.getElementById("banner");
const profile = document.getElementById("profile");
const copyEmail = document.getElementById("copy-email");
const copyHint = document.getElementById("copy-hint");
const photo = document.getElementById("photo");
const initials = document.getElementById("initials");
const photoFile = document.getElementById("photo-file");
const photoChoose = document.getElementById("photo-choose");
const photoRemove = document.getElementById("photo-remove");
const dialog = document.getElementById("details-dialog");
const form = document.getElementById("details-form");
const bioInput = document.getElementById("f-bio");
// The fields of the edit form, by the name the server uses.
const EDITABLE = ["bio", "skills", "personalEmail", "phone", "linkedinUrl", "githubUrl", "portfolioUrl"];
const inputs = Object.fromEntries(EDITABLE.map((name) => [name, document.getElementById("f-" + name)]));
const LINKS = [
  ["linkedinUrl", "LinkedIn", "briefcase"],
  ["githubUrl", "GitHub", "code"],
  ["portfolioUrl", "Portfolio", "globe"],
];
let student = null;

function show(id, text) {
  document.getElementById(id).textContent = text;
}

/** "https://www.linkedin.com/in/name/" is shown as "linkedin.com/in/name". */
function shortLink(url) {
  return url.replace(/^https:\/\//, "").replace(/^www\./, "").replace(/\/$/, "");
}

function renderLinks() {
  const rows = LINKS.filter(([field]) => student[field]).map(([field, label, iconName]) => {
    const anchor = h("a.info-list__value.clip", { href: student[field], target: "_blank", rel: "noopener noreferrer" }, shortLink(student[field]));
    return h("li.info-list__row", h("span.info-list__icon", icon(iconName)), h("span.info-list__text", h("span.info-list__label", label), anchor), icon("arrow-up-right", "info-list__go"));
  });
  fill(
    document.getElementById("links"),
    rows.length ? rows : h("li.info-list__empty", "Add your LinkedIn, GitHub or portfolio so others can find your work."),
  );
}

function renderSkills() {
  const skills = (student.skills || "").split(",").map((skill) => skill.trim()).filter(Boolean);
  const list = document.getElementById("skills");
  fill(list, skills.map((skill) => h("li.skill", skill)));
  list.hidden = !skills.length;
}

function render(loaded) {
  student = loaded;
  show("initials", initialsOf(student.fullName));
  show("hero-name", student.fullName);
  show("programme", `${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`);
  show("roll-chip", student.rollNo);
  show("admitted-chip", String(student.admissionYear));
  show("email-chip", student.email);
  show("email", student.email);

  show("roll-no", student.rollNo);
  show("branch", student.branch);
  show("semester", String(student.currentSemester));
  show("section", student.section);
  // Two semesters make one year of study.
  show("stat-year", String(Math.ceil(student.currentSemester / 2)));
  show("admission-year", String(student.admissionYear));

  const optional = (id, value) => {
    const element = document.getElementById(id);
    element.textContent = value || "Not added";
    element.classList.toggle("is-empty", !value);
  };
  optional("phone", student.phone);
  optional("personal-email", student.personalEmail);
  const bio = document.getElementById("bio");
  bio.textContent = student.bio || "Write a few lines about yourself: what you study, what you enjoy, what you are looking for.";
  bio.classList.toggle("profile-bio--empty", !student.bio);
  renderSkills();
  renderLinks();
  profile.hidden = false;
  showPhoto();
}

/** Shows the photo through its short-lived link, or the initials when there is no photo. */
async function showPhoto() {
  const label = student.hasPhoto ? "Change photo" : "Add photo";
  show("photo-choose-text", label);
  photoChoose.setAttribute("aria-label", label);
  photoRemove.hidden = !student.hasPhoto;
  if (!student.hasPhoto) {
    photo.hidden = true;
    photo.removeAttribute("src");
    initials.hidden = false;
    return;
  }
  try {
    const link = await api.get("/students/me/photo-url");
    photo.src = link.url;
    photo.hidden = false;
    initials.hidden = true;
  } catch {
    // File storage is not reachable. The initials stay, and the rest of the page works.
    photo.hidden = true;
    initials.hidden = false;
  }
}

// If the picture cannot be loaded after all, the initials come back.
photo.addEventListener("error", () => {
  photo.hidden = true;
  initials.hidden = false;
});

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

// ---------------------------------------------------------------- Photo
photoChoose.addEventListener("click", () => photoFile.click());

photoFile.addEventListener("change", async () => {
  const file = photoFile.files[0];
  photoFile.value = "";
  if (!file) {
    return;
  }
  // These two checks only save a wasted upload. The server checks the file itself again.
  if (!["image/jpeg", "image/png"].includes(file.type)) {
    toast("Use a JPEG or PNG photo");
    return;
  }
  if (file.size > MAX_PHOTO_BYTES) {
    toast("The photo is larger than 2 MB");
    return;
  }
  photoChoose.disabled = true;
  try {
    render(await api.putFile("/students/me/photo", file));
    toast("Photo saved");
  } catch (failure) {
    toast(messageFor(failure));
  } finally {
    photoChoose.disabled = false;
  }
});

photoRemove.addEventListener("click", async () => {
  photoRemove.disabled = true;
  try {
    await api.delete("/students/me/photo");
    render({ ...student, hasPhoto: false });
    toast("Photo removed");
  } catch (failure) {
    toast(messageFor(failure));
  } finally {
    photoRemove.disabled = false;
  }
});

// ---------------------------------------------------------------- Personal details
const bioCount = document.getElementById("bio-count");
const detailsError = document.getElementById("details-error");
const showBioCount = () => {
  bioCount.textContent = `${bioInput.value.length} / 280`;
};

document.getElementById("edit-details").addEventListener("click", () => {
  for (const name of EDITABLE) {
    inputs[name].value = student[name] || "";
    setFieldError(inputs[name], "");
  }
  detailsError.hidden = true;
  showBioCount();
  dialog.showModal();
});
bioInput.addEventListener("input", showBioCount);
dialog.addEventListener("click", (event) => {
  if (event.target === dialog || event.target.closest("[data-close]")) {
    dialog.close();
  }
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const save = document.getElementById("details-save");
  for (const name of EDITABLE) {
    setFieldError(inputs[name], "");
  }
  detailsError.hidden = true;
  save.disabled = true;
  try {
    // Only these fields are sent. The server refuses a request that names anything else.
    const body = Object.fromEntries(EDITABLE.map((name) => [name, inputs[name].value.trim() || null]));
    render(await api.put("/students/me", body));
    dialog.close();
    toast("Profile saved");
  } catch (failure) {
    const known = (failure.fieldErrors || []).filter((entry) => inputs[entry.field]);
    for (const entry of known) {
      setFieldError(inputs[entry.field], entry.message);
    }
    if (known.length) {
      inputs[known[0].field].focus();
    } else {
      detailsError.textContent = messageFor(failure);
      detailsError.hidden = false;
    }
  } finally {
    save.disabled = false;
  }
});

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
load();
