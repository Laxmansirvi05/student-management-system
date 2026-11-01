// Tests for what the Quick Assistant accepts as an attachment and how it words what was read.

import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ATTACHED_MARKER,
  DOCUMENT_MAX_BYTES,
  IMAGE_SEND_BYTES,
  IMAGE_SIDE,
  badgeOf,
  describeRead,
  jpegName,
  kindOf,
  needsRedraw,
  parseAttached,
  redrawnSize,
  refusal,
  statusLabel,
} from "../../main/resources/static/js/robot/attachment-rules.js";

const file = (name, type = "", size = 1000) => ({ name, type, size });

test("pictures, PDFs and Word documents are told apart by name or type", () => {
  assert.equal(kindOf(file("photo.JPG")), "image");
  assert.equal(kindOf(file("a.jpeg")), "image");
  assert.equal(kindOf(file("a.png")), "image");
  assert.equal(kindOf(file("a.webp")), "image");
  assert.equal(kindOf(file("blob", "image/webp")), "image");
  assert.equal(kindOf(file("paper.pdf")), "pdf");
  assert.equal(kindOf(file("x", "application/pdf")), "pdf");
  assert.equal(kindOf(file("notes.docx")), "docx");
  assert.equal(kindOf(file("x", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")), "docx");
});

test("other files are refused, each with a reason", () => {
  assert.equal(kindOf(file("a.txt")), null);
  assert.equal(kindOf(file("a.gif", "image/gif")), null);
  assert.match(refusal(file("a.txt")), /JPG, PNG, WEBP, PDF or DOCX/);
  assert.match(refusal(file("old.doc")), /\.docx or PDF/);
  assert.match(refusal(file("empty.pdf", "", 0)), /empty/);
  assert.match(refusal(file("big.pdf", "", DOCUMENT_MAX_BYTES + 1)), /10 MB/);
  assert.equal(refusal(file("ok.pdf")), null);
  assert.equal(refusal(file("ok.docx")), null);
});

test("a large photo is allowed: the page redraws it smaller before sending", () => {
  assert.equal(refusal(file("photo.jpg", "image/jpeg", 12 * 1024 * 1024)), null);
  assert.equal(needsRedraw(file("photo.jpg", "image/jpeg", IMAGE_SEND_BYTES + 1)), true);
  assert.equal(needsRedraw(file("photo.jpg", "image/jpeg", 200000)), false);
});

test("WEBP is always redrawn, because the server reads JPEG and PNG", () => {
  assert.equal(needsRedraw(file("a.webp", "image/webp", 1000)), true);
  assert.equal(needsRedraw(file("a.WEBP", "", 1000)), true);
  assert.equal(needsRedraw(file("a.pdf", "application/pdf", 100 * 1024 * 1024)), false);
});

test("a redrawn picture keeps its shape, is never enlarged, and never has an empty side", () => {
  assert.deepEqual(redrawnSize(4800, 2400), { width: IMAGE_SIDE, height: IMAGE_SIDE / 2 });
  assert.deepEqual(redrawnSize(1000, 500), { width: 1000, height: 500 });
  assert.deepEqual(redrawnSize(1, 100000), { width: 1, height: IMAGE_SIDE });
  assert.equal(jpegName("IMG_001.webp"), "IMG_001.jpg");
  assert.equal(jpegName("noext"), "noext.jpg");
  assert.equal(jpegName(""), "picture.jpg");
});

test("the three steps of attaching are worded", () => {
  assert.equal(statusLabel("uploading"), "Uploading…");
  assert.equal(statusLabel("reading"), "Reading…");
  assert.equal(statusLabel("ready"), "Ready");
  assert.equal(statusLabel("nonsense"), "");
});

test("what was read is described from the server's answer, not from the file name", () => {
  assert.equal(describeRead({ kind: "pdf", pages: 3, pagesRead: 3, pictures: 0, characters: 600 }), "PDF · 3 pages");
  assert.equal(describeRead({ kind: "pdf", pages: 1, pagesRead: 1, pictures: 1, characters: 0 }), "PDF · 1 page, 1 as a picture");
  assert.equal(describeRead({ kind: "pdf", pages: 90, pagesRead: 60, pictures: 2, characters: 9000 }), "PDF · 60 of 90 pages, 2 as pictures");
  assert.equal(describeRead({ kind: "docx", pages: 0, pagesRead: 0, pictures: 1, characters: 1204 }), "Word · 1,204 characters, 1 figure");
  assert.equal(describeRead({ kind: "image", pages: 0, pagesRead: 0, pictures: 1, characters: 0 }), "Picture");
  assert.equal(describeRead(null), "");
  assert.equal(badgeOf("pdf"), "PDF");
  assert.equal(badgeOf("docx"), "DOCX");
  assert.equal(badgeOf("image"), "IMG");
});

test("a saved question names its files in its first words", () => {
  assert.deepEqual(parseAttached("[Attached: paper.pdf, notes.jpg] Solve Q2"), { names: ["paper.pdf", "notes.jpg"], text: "Solve Q2" });
  assert.deepEqual(parseAttached("[Attached: a.docx] Read the attached file."), { names: ["a.docx"], text: "Read the attached file." });
  assert.deepEqual(parseAttached("Just a question"), { names: [], text: "Just a question" });
  assert.deepEqual(parseAttached("[Attached: broken"), { names: [], text: "[Attached: broken" });
  assert.deepEqual(parseAttached(undefined), { names: [], text: "" });
  assert.ok(ATTACHED_MARKER.startsWith("[Attached"));
});
