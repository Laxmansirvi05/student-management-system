import { api, messageFor } from "./api.js";
import { h, toast } from "./dom.js";
import { clearErrorsOnInput, setFieldError } from "./forms.js";
import { reduceMotionChosen, setReduceMotion } from "./preferences.js";
import { currentUser } from "./shell.js";

// Settings, for students and for administrators: change the password, choose less motion, and
// read the privacy policy and the terms. The same page script serves both portals.

const form = document.getElementById("password-form");
const current = document.getElementById("currentPassword");
const next = document.getElementById("newPassword");
const confirm = document.getElementById("confirmNewPassword");
const save = document.getElementById("password-save");
const problem = document.getElementById("password-error");
const fields = { currentPassword: current, newPassword: next, confirmNewPassword: confirm };
const ROLES = { STUDENT: "Student", ADMIN: "Administrator" };

// A password form should say whose password it is, so that a password manager stores the new one
// for the right account (and the browser stops warning about it). The field is never shown or sent.
const username = h("input", { type: "text", name: "username", autocomplete: "username", hidden: true, tabindex: "-1", "aria-hidden": "true", readonly: true });
form.prepend(username);

currentUser
  .then((user) => {
    document.getElementById("account-name").textContent = user.displayName;
    document.getElementById("account-email").textContent = user.email;
    // As the field's default, so that resetting the form after a change keeps it.
    username.setAttribute("value", user.email);
    document.getElementById("account-role").textContent = ROLES[user.role] || user.role;
  })
  .catch(() => {});

// ---------------------------------------------------------------- Password
clearErrorsOnInput([current, next, confirm]);
document.getElementById("show-passwords").addEventListener("change", (event) => {
  for (const input of Object.values(fields)) {
    input.type = event.target.checked ? "text" : "password";
  }
});

/** The same checks the server makes first, so an obvious slip is caught without a request. */
function looksRight() {
  for (const input of Object.values(fields)) {
    setFieldError(input, "");
  }
  if (!current.value) {
    setFieldError(current, "Enter your current password.");
    current.focus();
    return false;
  }
  if (next.value.length < 8) {
    setFieldError(next, "Use at least 8 characters.");
    next.focus();
    return false;
  }
  if (next.value !== confirm.value) {
    setFieldError(confirm, "The two passwords do not match.");
    confirm.focus();
    return false;
  }
  return true;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  problem.hidden = true;
  if (!looksRight()) {
    return;
  }
  save.disabled = true;
  try {
    await api.post("/auth/change-password", { currentPassword: current.value, newPassword: next.value, confirmNewPassword: confirm.value });
    form.reset();
    toast("Password changed. Other devices were logged out.");
  } catch (failure) {
    const messages = {};
    for (const entry of failure.fieldErrors || []) {
      if (fields[entry.field]) {
        messages[entry.field] = [messages[entry.field], entry.message].filter(Boolean).join(" ");
      }
    }
    const names = Object.keys(messages);
    for (const name of names) {
      setFieldError(fields[name], messages[name]);
    }
    if (names.length) {
      fields[names[0]].focus();
    } else {
      problem.textContent = messageFor(failure);
      problem.hidden = false;
    }
  } finally {
    save.disabled = false;
  }
});

// ---------------------------------------------------------------- Display
const reduceMotion = document.getElementById("reduce-motion");
reduceMotion.checked = reduceMotionChosen();
reduceMotion.addEventListener("change", () => {
  setReduceMotion(reduceMotion.checked);
  toast(reduceMotion.checked ? "Animations are off on this device" : "Animations are on again");
});
