import { api, messageFor } from "./api.js";
import { toast } from "./dom.js";
import { setFieldError, showBanner } from "./forms.js";
import { countUp, enableTilt } from "./motion.js";
import { initialsOf } from "./shell.js";

// The student's own profile (FR-STU-002, FR-STU-003). What the college manages is read-only. The
// student can change three things here: their photo, their phone number and a short text about
// themselves. The server decides whose profile it is from the session; no ID is ever sent.

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
const phoneInput = document.getElementById("f-phone");
const bioInput = document.getElementById("f-bio");
let student = null;

function show(id, text) {
  document.getElementById(id).textContent = text;
}

function render(loaded, animate = true) {
  student = loaded;
  show("initials", initialsOf(student.fullName));
  show("hero-name", student.fullName);
  show("programme", `${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`);
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
  const bio = document.getElementById("bio");
  bio.textContent = student.bio || "You have not written anything about yourself yet.";
  bio.classList.toggle("profile-bio--empty", !student.bio);

  show("stat-section", student.section);
  profile.hidden = false;
  if (animate) {
    countUp(document.getElementById("stat-semester"), student.currentSemester);
    // Two semesters make one year of study.
    countUp(document.getElementById("stat-year"), Math.ceil(student.currentSemester / 2));
    countUp(document.getElementById("stat-admission"), student.admissionYear);
  }
  showPhoto();
}

/** Shows the photo through its short-lived link, or the initials when there is no photo. */
async function showPhoto() {
  show("photo-choose-text", student.hasPhoto ? "Change photo" : "Add photo");
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
    render(await api.putFile("/students/me/photo", file), false);
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
    render({ ...student, hasPhoto: false }, false);
    toast("Photo removed");
  } catch (failure) {
    toast(messageFor(failure));
  } finally {
    photoRemove.disabled = false;
  }
});

// ---------------------------------------------------------------- Phone and text
const bioCount = document.getElementById("bio-count");
const detailsError = document.getElementById("details-error");
const showBioCount = () => {
  bioCount.textContent = `${bioInput.value.length} / 280`;
};

document.getElementById("edit-details").addEventListener("click", () => {
  phoneInput.value = student.phone || "";
  bioInput.value = student.bio || "";
  setFieldError(phoneInput, "");
  setFieldError(bioInput, "");
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
  setFieldError(phoneInput, "");
  setFieldError(bioInput, "");
  detailsError.hidden = true;
  save.disabled = true;
  try {
    // Only these two fields are sent. The server refuses a request that names anything else.
    render(await api.put("/students/me", { phone: phoneInput.value.trim() || null, bio: bioInput.value.trim() || null }), false);
    dialog.close();
    toast("Saved");
  } catch (failure) {
    const fields = { phone: phoneInput, bio: bioInput };
    const known = (failure.fieldErrors || []).filter((entry) => fields[entry.field]);
    for (const entry of known) {
      setFieldError(fields[entry.field], entry.message);
    }
    if (known.length) {
      fields[known[0].field].focus();
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
enableTilt();
load();
