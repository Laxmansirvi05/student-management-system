// What the Quick Assistant accepts as an attachment, and how an attachment is described. No DOM, so
// it can be tested on its own. The server decides what a file really is (from its first bytes) and
// reads it; these rules only keep an obviously wrong file from being sent, and word what is shown.

/** The most pictures that can be used in one question (the server allows the same). */
export const MAX_FILES = 4;
/** A picture larger than this is redrawn smaller by the page before it is sent (the server takes 5 MB). */
export const IMAGE_SEND_BYTES = 4 * 1024 * 1024;
/** The longest side of a redrawn picture, in pixels. */
export const IMAGE_SIDE = 2400;

export const ATTACHED_MARKER = "[Attached: ";

/** "image" for a JPG, PNG or WEBP picture, otherwise null. */
export function kindOf(file) {
  const name = String(file?.name ?? "").toLowerCase();
  const type = String(file?.type ?? "").toLowerCase();
  return /\.(jpe?g|png|webp)$/.test(name) || /^image\/(jpeg|png|webp)$/.test(type) ? "image" : null;
}

/** The reason a file cannot be attached, or null when it can. */
export function refusal(file) {
  if (!kindOf(file)) return "Attach a JPG, PNG or WEBP picture.";
  if (!file.size) return "That picture is empty.";
  return null;
}

/** Whether a picture must be redrawn before it is sent: WEBP (the server reads JPEG and PNG) or large. */
export function needsRedraw(file) {
  return (/webp/i.test(file.type) || /\.webp$/i.test(file.name) || file.size > IMAGE_SEND_BYTES);
}

/** The size a redrawn picture gets: no side longer than IMAGE_SIDE, never enlarged. */
export function redrawnSize(width, height) {
  const scale = Math.min(1, IMAGE_SIDE / Math.max(width, height, 1));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** The new name of a redrawn picture: the same name with .jpg. */
export function jpegName(name) {
  const base = String(name || "picture").replace(/\.[^.]*$/, "") || "picture";
  return `${base}.jpg`;
}

/** What the student sees while a file is being attached. */
export function statusLabel(status) {
  return { preparing: "Preparing…", uploading: "Uploading…", reading: "Reading…", ready: "Ready" }[status] ?? "";
}

/** A short description of what was really read, from the server's answer. */
export function describeRead(info) {
  if (!info) return "";
  return info.pictures > 1 ? `Picture · ${info.pictures} pictures` : "Picture";
}

/**
 * A stored question starts with "[Attached: a.jpg, b.png] " when files came with it. Returns the
 * names and the question without that start (names is empty when there was none).
 */
export function parseAttached(content) {
  const text = String(content ?? "");
  if (!text.startsWith(ATTACHED_MARKER)) return { names: [], text };
  const end = text.indexOf("] ");
  if (end < 0) return { names: [], text };
  const names = text.slice(ATTACHED_MARKER.length, end).split(", ").filter(Boolean);
  return { names, text: text.slice(end + 2) };
}
