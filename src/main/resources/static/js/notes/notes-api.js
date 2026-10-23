import { api } from "../api.js";

// The one client-side entry point for every note write, whether it comes from the notes
// workspace, the quick-note composer or the robot: there is one notes API, and this is its client.

const BASE = "/notes";

/** Notes for a view. Options: q, view, sort, folder (an ID or "unfiled"), tag, attachmentsOnly. */
export async function listNotes({ q, view, sort, folder, tag, attachmentsOnly } = {}) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (view) params.set("view", view);
  if (sort) params.set("sort", sort);
  if (folder) params.set("folder", folder);
  if (tag) params.set("tag", tag);
  if (attachmentsOnly) params.set("attachments", "true");
  const result = await api.get(BASE + (params.size ? "?" + params : ""));
  return result.notes;
}

export const getNote = (id) => api.get(`${BASE}/${id}`);

export const createNote = (input, options) => api.post(BASE, input, options);

/**
 * Returns the saved note, or null when the update left the note empty and the server deleted it:
 * the "draft became empty again" path of the quick-note composer.
 */
export async function updateNote(id, input, options) {
  const result = await api.patch(`${BASE}/${id}`, input, options);
  return result.deleted ? null : result.note;
}

/** Moves a note to the Trash. Pass permanent to destroy it for good. */
export const deleteNote = (id, permanent = false) => api.delete(`${BASE}/${id}${permanent ? "?permanent=true" : ""}`);

export const restoreNote = (id) => updateNote(id, { restore: true });

export const duplicateNote = (id) => api.post(`${BASE}/${id}/duplicate`);

export async function bulkNoteAction(ids, action, folderId = null) {
  const result = await api.post(`${BASE}/bulk`, { ids, action, folderId });
  return result.count;
}

export async function emptyTrash() {
  const result = await api.post(`${BASE}/bulk`, { action: "empty_trash" });
  return result.count;
}

// --- folders --------------------------------------------------------------------------------

export async function listFolders() {
  return (await api.get(`${BASE}/folders`)).folders;
}

export const createFolder = (input) => api.post(`${BASE}/folders`, input);

export const updateFolder = (id, input) => api.patch(`${BASE}/folders/${id}`, input);

export const deleteFolder = (id) => api.delete(`${BASE}/folders/${id}`);

/** order: [{ id, position, parentId?, setParent? }] */
export const reorderFolders = (order) => api.patch(`${BASE}/folders`, { order });

// --- sharing --------------------------------------------------------------------------------

export async function getShare(noteId) {
  return (await api.get(`${BASE}/${noteId}/share`)).share;
}

export async function setLinkAccess(noteId, linkAccess) {
  return (await api.put(`${BASE}/${noteId}/share`, { linkAccess })).share;
}

export async function addShareRecipient(noteId, identifier, permission) {
  return (await api.post(`${BASE}/${noteId}/share/recipients`, { identifier, permission })).share;
}

export async function removeShareRecipient(noteId, recipientId) {
  return (await api.delete(`${BASE}/${noteId}/share/recipients/${recipientId}`)).share;
}

export async function stopSharing(noteId) {
  return (await api.delete(`${BASE}/${noteId}/share`)).share;
}

export const openSharedNote = (slug) => api.get(`${BASE}/shared/${encodeURIComponent(slug)}`);

// --- links between notes --------------------------------------------------------------------

export async function listLinks(noteId) {
  return (await api.get(`${BASE}/${noteId}/links`)).links;
}

export async function addLink(noteId, targetType, targetId) {
  return (await api.post(`${BASE}/${noteId}/links`, { targetType, targetId })).links;
}

export async function removeLink(noteId, linkId) {
  return (await api.delete(`${BASE}/${noteId}/links/${linkId}`)).links;
}

export async function searchLinkTargets(q, excludeNoteId) {
  const params = new URLSearchParams({ q });
  if (excludeNoteId) params.set("exclude", excludeNoteId);
  return (await api.get(`${BASE}/link-targets?${params}`)).targets;
}

// --- attachments ----------------------------------------------------------------------------

export async function listAttachments(kind) {
  return (await api.get(`${BASE}/attachments${kind ? "?kind=" + kind : ""}`)).attachments;
}

export const deleteAttachment = (id) => api.delete(`${BASE}/attachments/${id}`);

/**
 * Uploads an image or file for a note. The note ID is optional: the editor can upload before the
 * note exists, and the note adopts the file once its content refers to it.
 */
export function uploadNoteAttachment(file, noteId = null) {
  return api.postFile(`${BASE}/attachments${noteId ? "?noteId=" + noteId : ""}`, file);
}
