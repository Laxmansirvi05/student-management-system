import { createNote, deleteNote, updateNote } from "./notes-api.js";
import { isEmptyDocument } from "./note-text.js";

// The one autosave engine behind every note edit in the application: the notes workspace and the
// quick-note composer both use this, not two separate implementations. It creates a draft row on
// the first keystroke (so autosave has something to update from then on), and deletes the note
// instead of saving it if the writer empties it again: it never leaves a meaningless empty record.
//
// A save that fails for a passing reason (offline, rate limited, server busy) is tried again after
// a growing wait, or after the server's Retry-After. When the page is hidden or closed, anything
// not yet saved is sent at once with keepalive, so the browser finishes the request after the tab
// is gone.

const DEFAULT_DEBOUNCE_MS = 800;
const RETRY_DELAYS_MS = [1000, 2000, 4000, 8000, 16000, 30000];
const MAX_RETRY_AFTER_MS = 60000;

/** Whether a failed save is worth trying again: the request may succeed a little later. */
export function isTransient(error) {
  const status = error?.status;
  return status === 0 || status === 408 || status === 429 || (status >= 500 && status !== 501);
}

/**
 * Whether a failed create may be tried again without risking a second note. Only when the server
 * says it did not process the request: an offline error may have lost the answer to a create that
 * worked.
 */
function isSafeToRepeatCreate(error) {
  return error?.status === 429 || error?.status === 503;
}

/** How long to wait before the next try: the server's Retry-After in seconds, or the backoff. */
export function retryDelay(error, attempt) {
  const seconds = Number(error?.retryAfter);
  if (Number.isFinite(seconds) && seconds > 0) {
    return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
  }
  return RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
}

/** Calls back when the page is hidden or closed. Returns a function that stops listening. */
function watchPageHide(callback) {
  if (typeof window === "undefined" || typeof document === "undefined") {
    return () => {};
  }
  const onVisibility = () => {
    if (document.visibilityState === "hidden") callback();
  };
  window.addEventListener("pagehide", callback);
  document.addEventListener("visibilitychange", onVisibility);
  return () => {
    window.removeEventListener("pagehide", callback);
    document.removeEventListener("visibilitychange", onVisibility);
  };
}

/**
 * Options: note (an existing note, or null to create on first keystroke), source ("manual" or
 * "robot", only used when creating), folderId (for a new note), draft ({title, content} to start a
 * new note from a template), debounceMs, onSaved(note), onDeleted(), onStatus(status).
 * Status is "idle", "saving", "saved", "retrying" or "error".
 * client and watchPage replace the server calls and the page events, for tests.
 */
export function createAutosave({
  note = null,
  source = "manual",
  folderId = null,
  draft = null,
  debounceMs = DEFAULT_DEBOUNCE_MS,
  onSaved = () => {},
  onDeleted = () => {},
  onStatus = () => {},
  client = { createNote, deleteNote, updateNote },
  watchPage = watchPageHide,
}) {
  let title = note?.title ?? draft?.title ?? "";
  let content = note?.content ?? draft?.content ?? "";
  let noteId = note?.id ?? null;
  let status = "idle";
  let timer = null;
  let retryTimer = null;
  let attempts = 0;
  // Every edit gets the next revision number; savedRevision is the newest one the server has.
  // A save of an older revision than that is skipped, so a late retry never overwrites newer text
  // and nothing already saved is sent twice.
  // A new note started from a template counts as one unsaved edit, so closing it untouched still
  // creates it, as before.
  let revision = !note && draft ? 1 : 0;
  let savedRevision = 0;
  // Guards a debounce tick against acting on stale text after flush() already ran.
  let flushed = false;
  // Saves run one at a time. noteId is written the moment a draft row exists, which is enough for
  // sequential saves but not for overlapping ones: a tick that has already called createNote is
  // still waiting for the network when flush() runs on close, and both would see no id and create
  // their own row. Chaining makes the second save start only after the first recorded the id.
  let queue = Promise.resolve();

  const setStatus = (next) => {
    status = next;
    onStatus(next);
  };

  const hasUnsavedChanges = () => revision > savedRevision;

  function retryLater(error) {
    const wait = retryDelay(error, attempts);
    attempts += 1;
    setStatus("retrying");
    clearTimeout(retryTimer);
    retryTimer = setTimeout(() => {
      retryTimer = null;
      if (hasUnsavedChanges()) persist(title, content, revision);
    }, wait);
  }

  async function run(nextTitle, nextContent, forRevision, { keepalive = false } = {}) {
    if (forRevision <= savedRevision) {
      return;
    }
    const empty = !nextTitle.trim() && isEmptyDocument(nextContent);
    if (empty) {
      if (noteId) {
        try {
          await client.deleteNote(noteId);
        } catch {
          // The row sits as an empty draft if this fails: not worth an error for a note the writer
          // already decided they did not want.
        }
        noteId = null;
        onDeleted();
      }
      savedRevision = forRevision;
      setStatus("idle");
      return;
    }
    setStatus("saving");
    const creating = !noteId;
    try {
      if (creating) {
        const created = await client.createNote({ title: nextTitle.trim(), content: nextContent, source, folderId }, { keepalive });
        noteId = created.id;
        savedRevision = forRevision;
        attempts = 0;
        setStatus("saved");
        onSaved(created);
      } else {
        const updated = await client.updateNote(noteId, { title: nextTitle.trim(), content: nextContent }, { keepalive });
        savedRevision = forRevision;
        attempts = 0;
        if (updated === null) {
          // Emptied by this very update: the server is the authority on what "empty" means.
          noteId = null;
          onDeleted();
        } else {
          setStatus("saved");
          onSaved(updated);
        }
      }
    } catch (error) {
      const repeatable = creating ? isSafeToRepeatCreate(error) : isTransient(error);
      if (repeatable && attempts < RETRY_DELAYS_MS.length) {
        retryLater(error);
      } else {
        setStatus("error");
      }
    }
  }

  function persist(nextTitle, nextContent, forRevision, options) {
    const queued = queue.then(() => run(nextTitle, nextContent, forRevision, options));
    // Swallowed on the shared chain only: run() reports failure through the status, and a rejection
    // left here would break every later save queued behind it.
    queue = queued.catch(() => {});
    return queued;
  }

  function schedule() {
    revision += 1;
    flushed = false;
    clearTimeout(timer);
    timer = setTimeout(() => {
      if (!flushed) persist(title, content, revision);
    }, debounceMs);
  }

  // The tab is going away or into the background: send what is not saved yet, now.
  const stopWatching = watchPage(() => {
    if (!hasUnsavedChanges()) return;
    clearTimeout(timer);
    clearTimeout(retryTimer);
    timer = null;
    retryTimer = null;
    persist(title, content, revision, { keepalive: true });
  });

  return {
    get title() {
      return title;
    },
    get content() {
      return content;
    },
    get noteId() {
      return noteId;
    },
    get status() {
      return status;
    },
    setTitle(value) {
      title = value;
      schedule();
    },
    setContent(value) {
      content = value;
      schedule();
    },
    /** Cancels any pending wait and saves (or deletes if empty) now. Resolves when it is done. */
    async flush() {
      flushed = true;
      clearTimeout(timer);
      clearTimeout(retryTimer);
      timer = null;
      retryTimer = null;
      if (!hasUnsavedChanges()) {
        await queue;
        return;
      }
      await persist(title, content, revision);
    },
    /** Stops timers and page listeners without saving: the pane is being torn down after a flush. */
    dispose() {
      clearTimeout(timer);
      clearTimeout(retryTimer);
      stopWatching();
    },
  };
}
