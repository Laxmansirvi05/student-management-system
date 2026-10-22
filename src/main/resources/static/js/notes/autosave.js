import { createNote, deleteNote, updateNote } from "./notes-api.js";
import { isEmptyDocument } from "./note-text.js";

// The one autosave engine behind every note edit in the application: the notes workspace and the
// quick-note composer both use this, not two separate implementations. It creates a draft row on
// the first keystroke (so autosave has something to update from then on), and deletes the note
// instead of saving it if the writer empties it again: it never leaves a meaningless empty record.

const DEFAULT_DEBOUNCE_MS = 800;

/**
 * Options: note (an existing note, or null to create on first keystroke), source ("manual" or
 * "robot", only used when creating), folderId (for a new note), draft ({title, content} to start a
 * new note from a template), debounceMs, onSaved(note), onDeleted(), onStatus(status).
 * Status is "idle", "saving", "saved" or "error".
 */
export function createAutosave({ note = null, source = "manual", folderId = null, draft = null, debounceMs = DEFAULT_DEBOUNCE_MS, onSaved = () => {}, onDeleted = () => {}, onStatus = () => {} }) {
  let title = note?.title ?? draft?.title ?? "";
  let content = note?.content ?? draft?.content ?? "";
  let noteId = note?.id ?? null;
  let status = "idle";
  let timer = null;
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

  async function run(nextTitle, nextContent) {
    const empty = !nextTitle.trim() && isEmptyDocument(nextContent);
    if (empty) {
      if (noteId) {
        try {
          await deleteNote(noteId);
        } catch {
          // The row sits as an empty draft if this fails: not worth an error for a note the writer
          // already decided they did not want.
        }
        noteId = null;
        onDeleted();
      }
      setStatus("idle");
      return;
    }
    setStatus("saving");
    try {
      if (!noteId) {
        const created = await createNote({ title: nextTitle.trim(), content: nextContent, source, folderId });
        noteId = created.id;
        setStatus("saved");
        onSaved(created);
      } else {
        const updated = await updateNote(noteId, { title: nextTitle.trim(), content: nextContent });
        if (updated === null) {
          // Emptied by this very update: the server is the authority on what "empty" means.
          noteId = null;
          onDeleted();
        } else {
          setStatus("saved");
          onSaved(updated);
        }
      }
    } catch {
      setStatus("error");
    }
  }

  function persist(nextTitle, nextContent) {
    const queued = queue.then(() => run(nextTitle, nextContent));
    // Swallowed on the shared chain only: run() reports failure through the status, and a rejection
    // left here would break every later save queued behind it.
    queue = queued.catch(() => {});
    return queued;
  }

  function schedule() {
    flushed = false;
    clearTimeout(timer);
    timer = setTimeout(() => {
      if (!flushed) persist(title, content);
    }, debounceMs);
  }

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
      timer = null;
      await persist(title, content);
    },
    /** Stops timers without saving: the pane is being torn down after a flush. */
    dispose() {
      clearTimeout(timer);
    },
  };
}
