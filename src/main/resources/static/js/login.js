import { api, homeFor, messageFor } from "./api.js";
import {
  clearErrorsOnInput,
  setBusy,
  setFieldError,
  showBanner,
  wirePasswordToggle,
} from "./forms.js";
import { startMascot } from "./mascot.js";

const form = document.getElementById("login-form");
const email = document.getElementById("email");
const password = document.getElementById("password");
const submit = document.getElementById("submit");
const banner = document.getElementById("banner");

// Deliberately simple: the server decides what is valid. This only catches obvious slips.
const LOOKS_LIKE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

startMascot({ panel: document.querySelector(".auth-art"), passwordInputs: [password] });
wirePasswordToggle(document.getElementById("toggle-password"), [password]);
clearErrorsOnInput([email, password]);

if (sessionStorage.getItem("sessionExpired")) {
  sessionStorage.removeItem("sessionExpired");
  showBanner(banner, "error", "Your session has expired. Please log in again.");
}

// Someone who is already signed in has no use for this page.
api
  .get("/auth/me", { redirectOnAuthFailure: false })
  .then((user) => window.location.replace(homeFor(user)))
  .catch(() => {});

function validate() {
  setFieldError(email, "");
  setFieldError(password, "");
  const address = email.value.trim();
  if (!address && !password.value) {
    showBanner(banner, "error", "Enter your email and password.");
    email.focus();
    return false;
  }
  if (!LOOKS_LIKE_EMAIL.test(address)) {
    setFieldError(email, "Enter a valid email address.");
    email.focus();
    return false;
  }
  if (!password.value) {
    setFieldError(password, "Enter your password.");
    password.focus();
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
    const user = await api.post(
      "/auth/login",
      { email: email.value.trim(), password: password.value },
      { redirectOnAuthFailure: false },
    );
    window.location.assign(homeFor(user));
  } catch (error) {
    const emailError = error.fieldErrors?.find((entry) => entry.field === "email");
    if (emailError) {
      setFieldError(email, emailError.message);
      email.focus();
    } else {
      showBanner(banner, "error", messageFor(error));
      password.value = "";
      password.focus();
    }
    setBusy(submit, false);
  }
});
