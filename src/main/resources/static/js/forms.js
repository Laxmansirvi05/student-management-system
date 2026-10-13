// Small helpers shared by the forms: showing messages and field errors, and the busy state of
// a submit button. Text is always inserted with textContent, never as HTML.

/** Shows a page-level message. kind is "error" or "success". Passing no text hides it. */
export function showBanner(banner, kind, text) {
  if (!text) {
    banner.hidden = true;
    return;
  }
  banner.className = "banner banner--" + kind;
  banner.setAttribute("role", kind === "error" ? "alert" : "status");
  banner.querySelector(".banner__label").textContent = kind === "error" ? "Error" : "Done";
  banner.querySelector(".banner__text").textContent = text;
  banner.hidden = false;
}

/** Shows or clears the error under one field. */
export function setFieldError(input, text) {
  const error = document.getElementById(input.id + "-error");
  if (text) {
    input.setAttribute("aria-invalid", "true");
    error.textContent = text;
    error.hidden = false;
  } else {
    input.removeAttribute("aria-invalid");
    error.textContent = "";
    error.hidden = true;
  }
}

/** Removes a field's error as soon as the user starts correcting it. */
export function clearErrorsOnInput(inputs) {
  for (const input of inputs) {
    input.addEventListener("input", () => {
      if (input.hasAttribute("aria-invalid")) {
        setFieldError(input, "");
      }
    });
  }
}

/** Disables the button and shows a spinner while a request is running. */
export function setBusy(button, busy) {
  button.disabled = busy;
  button.querySelector(".spinner").hidden = !busy;
}

/** Wires a "show password" button to one or more password inputs. */
export function wirePasswordToggle(button, inputs) {
  button.addEventListener("click", () => {
    const show = inputs[0].type === "password";
    for (const input of inputs) {
      input.type = show ? "text" : "password";
    }
    button.setAttribute("aria-pressed", String(show));
    button.setAttribute("aria-label", show ? "Hide password" : "Show password");
  });
}
