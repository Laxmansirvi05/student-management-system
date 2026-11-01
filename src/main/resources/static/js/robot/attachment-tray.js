import { h, icon } from "../dom.js";
import { api, messageFor } from "../api.js";
import { symbol } from "../notes/symbols.js";
import { MAX_FILES, badgeOf, describeRead, jpegName, kindOf, needsRedraw, redrawnSize, refusal, statusLabel } from "./attachment-rules.js";

// The files of a Quick Assistant question, shown above the message box. A file goes to the server as
// soon as it is attached, and the server reads it (a picture is checked, a PDF gives its pages, a
// Word document its text) and answers with what it really found. A file is "Ready" only when that
// answer has come back, so a preview is never shown as a processed file when it is not. The files
// stay for the whole chat, so follow-up questions can use them, until the student removes them.

/**
 * Options: onChange() after any change, onNotice(text) for a refusal that has no row of its own,
 * onRecapture(key) for the button of a screen capture that takes the area again.
 * Returns { element, add, remove, clear, expireAll, ids, files, busy, problem, count }.
 */
export function createAttachmentTray({ onChange, onNotice, onRecapture }) {
  const element = h("div.rb-attach", { hidden: true, "aria-live": "polite" });
  let items = [];
  let counter = 0;

  function changed() {
    render();
    onChange();
  }

  /** Starts attaching a file. Returns false (with a notice) when it cannot be attached. */
  function add(file, { captured = false } = {}) {
    if (!file) return false;
    if (items.length >= MAX_FILES) {
      onNotice(`You can attach up to ${MAX_FILES} files to one question. Remove one first.`);
      return false;
    }
    const reason = refusal(file);
    if (reason) {
      onNotice(reason);
      return false;
    }
    const kind = kindOf(file);
    const item = {
      key: ++counter,
      file,
      name: file.name || (kind === "image" ? "Picture" : "File"),
      kind,
      url: kind === "image" ? URL.createObjectURL(file) : null,
      status: needsRedraw(file) ? "preparing" : "uploading",
      error: null,
      info: null,
      id: null,
      captured,
      large: false,
      removed: false,
    };
    items = [...items, item];
    changed();
    send(item);
    return true;
  }

  async function send(item) {
    item.error = null;
    try {
      const upload = needsRedraw(item.file) ? await redraw(item.file) : item.file;
      item.status = "uploading";
      render();
      const info = await api.uploadFile("/assistant/attachments", upload, () => {
        if (!item.removed) {
          item.status = "reading";
          render();
        }
      });
      if (item.removed) {
        api.delete(`/assistant/attachments/${info.id}`).catch(() => {});
        return;
      }
      item.info = info;
      item.id = info.id;
      item.status = "ready";
    } catch (failure) {
      if (item.removed) return;
      item.status = "error";
      item.error = failure instanceof Error && failure.name === "RedrawError" ? failure.message : messageFor(failure);
    }
    changed();
  }

  /** The picture as a JPEG of a sensible size: a WEBP the server cannot read, or a photo too large to send. */
  async function redraw(file) {
    let bitmap;
    try {
      bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    } catch {
      const failure = new Error("This picture could not be opened in this browser. Use a JPG or PNG.");
      failure.name = "RedrawError";
      throw failure;
    }
    const { width, height } = redrawnSize(bitmap.width, bitmap.height);
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close?.();
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.9));
    if (!blob) {
      const failure = new Error("This picture could not be prepared. Use a JPG or PNG.");
      failure.name = "RedrawError";
      throw failure;
    }
    return new File([blob], jpegName(file.name), { type: "image/jpeg" });
  }

  function retry(item) {
    item.status = needsRedraw(item.file) ? "preparing" : "uploading";
    item.error = null;
    changed();
    send(item);
  }

  /** Takes a file away, and tells the server to forget it. */
  function remove(key) {
    const item = items.find((entry) => entry.key === key);
    if (!item) return;
    item.removed = true;
    if (item.url) URL.revokeObjectURL(item.url);
    if (item.bubbleUrl) URL.revokeObjectURL(item.bubbleUrl);
    if (item.id) api.delete(`/assistant/attachments/${item.id}`).catch(() => {});
    items = items.filter((entry) => entry.key !== key);
    changed();
  }

  function clear() {
    for (const item of [...items]) remove(item.key);
  }

  /** The server no longer has the files (they are kept for a short time): each must be attached again. */
  function expireAll() {
    for (const item of items) {
      if (item.status === "ready") {
        item.status = "error";
        item.id = null;
        item.info = null;
        item.error = "No longer available. Press Retry to attach it again.";
      }
    }
    changed();
  }

  // --- what the chat needs to know ---------------------------------------------------------

  const ids = () => items.filter((item) => item.status === "ready").map((item) => item.id);
  const busy = () => items.some((item) => ["preparing", "uploading", "reading"].includes(item.status));
  const problem = () => items.some((item) => item.status === "error");
  /** The ready files as the chat shows them: name, kind, and an address for a picture. */
  const files = () =>
    items
      .filter((item) => item.status === "ready")
      .map((item) => {
        if (item.kind === "image" && !item.bubbleUrl) item.bubbleUrl = URL.createObjectURL(item.file);
        return { name: item.name, kind: item.kind, url: item.kind === "image" ? item.bubbleUrl : null };
      });

  // --- drawing -----------------------------------------------------------------------------

  function render() {
    element.hidden = items.length === 0;
    element.replaceChildren(...items.map(row));
  }

  function row(item) {
    const thumb =
      item.kind === "image"
        ? (() => {
            const button = h(
              "button.rb-attach__zoom",
              { type: "button", "aria-label": item.large ? "Show the picture smaller" : "Show the picture larger", "aria-pressed": String(item.large) },
              h("img.rb-attach__thumb", { src: item.url, alt: `Picture ${item.name}` }),
            );
            button.addEventListener("click", () => {
              item.large = !item.large;
              render();
            });
            return button;
          })()
        : h("span.rb-attach__badge", { "data-kind": item.kind, "aria-hidden": "true" }, badgeOf(item.kind));
    const meta = item.status === "error" ? h("span.rb-attach__meta.is-error", { role: "alert" }, item.error) : h("span.rb-attach__meta" + (item.status === "ready" ? ".is-ready" : ".is-working"), steps(item));
    return h(
      "div.rb-attach__item" + (item.large ? ".is-large" : "") + (item.status === "error" ? ".is-error" : ""),
      thumb,
      h(
        "div.rb-attach__text",
        h("span.rb-attach__name", { title: item.name }, item.name),
        meta,
        item.status === "ready" && item.info?.notice ? h("span.rb-attach__note", item.info.notice) : null,
      ),
      item.captured ? h("button.rb-iconbtn", { type: "button", "aria-label": "Capture the area again", title: "Capture again", onclick: () => onRecapture(item.key) }, icon("monitor")) : null,
      item.status === "error" ? h("button.rb-iconbtn", { type: "button", "aria-label": `Try ${item.name} again`, title: "Retry", onclick: () => retry(item) }, icon("refresh")) : null,
      h("button.rb-iconbtn", { type: "button", "aria-label": `Remove ${item.name}`, title: "Remove", onclick: () => remove(item.key) }, symbol("close")),
    );
  }

  /** "Reading…" while working; "Ready · PDF · 3 pages" once the server has read it. */
  function steps(item) {
    if (item.status !== "ready") return statusLabel(item.status);
    return [icon("check"), `${statusLabel("ready")} · ${describeRead(item.info)}`];
  }

  return { element, add, remove, clear, expireAll, ids, files, busy, problem, count: () => items.length };
}
