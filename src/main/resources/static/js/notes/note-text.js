// Plain facts read out of the HTML of a note, for previews and for deciding whether a note is
// empty. Display helpers only: the HTML has already been through the server's sanitizer.

const SNIPPET_MAX = 160;

/** The text of a document with the tags removed and the spaces tidied. */
export function stripHtml(html) {
  return (html || "")
    .replace(/<\/(p|div|li|h[1-6]|tr|blockquote|pre)>/gi, " ")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]*>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

export function toSnippet(html, max = SNIPPET_MAX) {
  const text = stripHtml(html);
  if (text.length <= max) {
    return text;
  }
  // Cut at a word when one is near the end, but not if that throws away most of the snippet.
  const cut = text.slice(0, max);
  const lastSpace = cut.lastIndexOf(" ");
  return (lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut) + "…";
}

/** The first inline image of the document, used as a card thumbnail. */
export function firstImageUrl(html) {
  const match = /<img[^>]+src=["']([^"']+)["']/i.exec(html || "");
  return match ? match[1] : null;
}

/** Checklist progress for a card badge, or null when the note has no checklist. */
export function checklistProgress(html) {
  const items = (html || "").match(/data-checked=["'](true|false)["']/gi);
  if (!items || items.length === 0) {
    return null;
  }
  return { done: items.filter((item) => /true/i.test(item)).length, total: items.length };
}

export function hasAttachmentMarkup(html) {
  return /<img\b/i.test(html || "") || /data-note-file/i.test(html || "");
}

/** True when the document shows nothing: no text and no picture. */
export function isEmptyDocument(html) {
  return stripHtml(html) === "" && !/<img\b/i.test(html || "");
}

/** "340 KB" or "2.4 MB" for a size in bytes, "" for none. */
export function formatBytes(bytes) {
  if (!bytes) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** "3m ago", "2h ago", "5d ago", or a short date, from an ISO timestamp. */
export function timeAgo(iso, now = Date.now()) {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString([], { month: "short", day: "numeric" });
}
