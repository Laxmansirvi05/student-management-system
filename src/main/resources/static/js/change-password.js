import { api, homeFor, LOGIN_PAGE, messageFor } from "./api.js";
import {
  clearErrorsOnInput,
  setBusy,
  setFieldError,
  showBanner,
  wirePasswordToggle,
} from "./forms.js";
import { startMascot } from "./mascot.js";

const form = document.getElementById("change-form");
const current = document.getElementById("currentPassword");
const next = document.getElementById("newPassword");
const confirm = document.getElementById("confirmNewPassword");
const submit = document.getElementById("submit");
const banner = document.getElementById("banner");
const intro = document.getElementById("intro");
const fields = { currentPassword: current, newPassword: next, confirmNewPassword: confirm };

startMascot({
  panel: document.querySelector(".auth-art"),
  passwordInputs: [current, next, confirm],
});
wirePasswordToggle(document.getElementById("toggle-password"), [current, next, confirm]);
clearErrorsOnInput([current, next, confirm]);

let user;
api
  .get("/auth/me")
  .then((me) => {
    user = me;
    if (me.mustChangePassword) {
      intro.textContent =
        "You are using a temporary password. Choose your own before you continue.";
    }
  })
  .catch(() => {});

document.getElementById("logout").addEventListener("click", async () => {
  await api.post("/auth/logout").catch(() => {});
  window.location.assign(LOGIN_PAGE);
});

function validate() {
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
  showBanner(banner, "error", "");
  if (!validate()) {
    return;
  }
  setBusy(submit, true);
  try {
    await api.post("/auth/change-password", {
      currentPassword: current.value,
      newPassword: next.value,
      confirmNewPassword: confirm.value,
    });
    window.location.assign(homeFor({ ...user, mustChangePassword: false }));
  } catch (error) {
    let first = null;
    const messages = {};
    for (const entry of error.fieldErrors || []) {
      if (fields[entry.field]) {
        messages[entry.field] = [messages[entry.field], entry.message].filter(Boolean).join(" ");
      }
    }
    for (const [name, text] of Object.entries(messages)) {
      setFieldError(fields[name], text);
      first = first || fields[name];
    }
    if (first) {
      first.focus();
    } else {
      showBanner(banner, "error", messageFor(error));
    }
    setBusy(submit, false);
  }
});
