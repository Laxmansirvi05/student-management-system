// What the Quick Assistant accepts as an attachment, and how an attachment is described. No DOM, so
// it can be tested on its own. The server decides what a file really is (from its first bytes) and
// reads it; these rules only keep an obviously wrong file from being sent, and word what is shown.

/** The most files that can be used in one question (the server allows the same). */
export const MAX_FILES = 4;
/** A PDF or a Word document, at most this many bytes (the server limit). */
export const DOCUMENT_MAX_BYTES = 10 * 1024 * 1024;
/** A picture larger than this is redrawn smaller by the page before it is sent (the server takes 5 MB). */
export const IMAGE_SEND_BYTES = 4 * 1024 * 1024;
/** The longest side of a redrawn picture, in pixels. */
export const IMAGE_SIDE = 2400;

export const ATTACHED_MARKER = "[Attached: ";

const DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/** "image", "pdf" or "docx" for a file this tool can read, otherwise null. */
export function kindOf(file) {
  const name = String(file?.name ?? "").toLowerCase();
  const type = String(file?.type ?? "").toLowerCase();
  if (/\.(jpe?g|png|webp)$/.test(name) || /^image\/(jpeg|png|webp)$/.test(type)) return "image";
  if (name.endsWith(".pdf") || type === "application/pdf") return "pdf";
  if (name.endsWith(".docx") || type === DOCX_TYPE) return "docx";
  return null;
}

/** The reason a file cannot be attached, or null when it can. */
export function refusal(file) {
  const kind = kindOf(file);
  if (!kind) {
    return /\.doc$/i.test(file?.name ?? "")
      ? "Old .doc files cannot be read. Save the document as .docx or PDF and attach that."
      : "Attach a JPG, PNG, WEBP, PDF or DOCX file.";
  }
  if (!file.size) return "That file is empty.";
  if (kind !== "image" && file.size > DOCUMENT_MAX_BYTES) return "The file is larger than 10 MB.";
  return null;
}

/** Whether a picture must be redrawn before it is sent: WEBP (the server reads JPEG and PNG) or large. */
export function needsRedraw(file) {
  return kindOf(file) === "image" && (/webp/i.test(file.type) || /\.webp$/i.test(file.name) || file.size > IMAGE_SEND_BYTES);
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
  const numbers = new Intl.NumberFormat("en");
  if (info.kind === "pdf") {
    const pages = info.pages === info.pagesRead ? `${info.pages} ${info.pages === 1 ? "page" : "pages"}` : `${info.pagesRead} of ${info.pages} pages`;
    const seen = info.pictures > 0 ? `, ${info.pictures} as ${info.pictures === 1 ? "a picture" : "pictures"}` : "";
    return `PDF · ${pages}${seen}`;
  }
  if (info.kind === "docx") {
    const figures = info.pictures > 0 ? `, ${info.pictures} ${info.pictures === 1 ? "figure" : "figures"}` : "";
    return `Word · ${numbers.format(info.characters)} characters${figures}`;
  }
  return "Picture";
}

/** A short label for the kind, for the badge on a file that has no thumbnail. */
export function badgeOf(kind) {
  return kind === "pdf" ? "PDF" : kind === "docx" ? "DOCX" : "IMG";
}

/**
 * A stored question starts with "[Attached: a.pdf, b.jpg] " when files came with it. Returns the
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
