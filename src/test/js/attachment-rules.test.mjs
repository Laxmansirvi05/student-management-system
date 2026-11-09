// Tests for what the Quick Assistant accepts as an attachment and how it words what was read.

import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ATTACHED_MARKER,
  IMAGE_SEND_BYTES,
  IMAGE_SIDE,
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

test("only JPG, PNG and WEBP pictures are accepted, by name or type", () => {
  assert.equal(kindOf(file("photo.JPG")), "image");
  assert.equal(kindOf(file("a.jpeg")), "image");
  assert.equal(kindOf(file("a.png")), "image");
  assert.equal(kindOf(file("a.webp")), "image");
  assert.equal(kindOf(file("blob", "image/webp")), "image");
  for (const other of ["paper.pdf", "notes.docx", "a.txt", "a.gif", "old.doc"]) assert.equal(kindOf(file(other)), null, other);
});

test("anything else is refused with a reason", () => {
  assert.match(refusal(file("paper.pdf")), /JPG, PNG or WEBP/);
  assert.match(refusal(file("empty.jpg", "", 0)), /empty/);
  assert.equal(refusal(file("ok.png")), null);
});

test("a large photo is allowed: the page redraws it smaller before sending", () => {
  assert.equal(refusal(file("photo.jpg", "image/jpeg", 12 * 1024 * 1024)), null);
  assert.equal(needsRedraw(file("photo.jpg", "image/jpeg", IMAGE_SEND_BYTES + 1)), true);
  assert.equal(needsRedraw(file("photo.jpg", "image/jpeg", 200000)), false);
});

test("WEBP is always redrawn, because the server reads JPEG and PNG", () => {
  assert.equal(needsRedraw(file("a.webp", "image/webp", 1000)), true);
  assert.equal(needsRedraw(file("a.WEBP", "", 1000)), true);
});

test("a redrawn picture keeps its shape, is never enlarged, and never has an empty side", () => {
  assert.deepEqual(redrawnSize(4800, 2400), { width: IMAGE_SIDE, height: IMAGE_SIDE / 2 });
  assert.deepEqual(redrawnSize(1000, 500), { width: 1000, height: 500 });
  assert.deepEqual(redrawnSize(1, 100000), { width: 1, height: IMAGE_SIDE });
  assert.equal(jpegName("IMG_001.webp"), "IMG_001.jpg");
  assert.equal(jpegName("noext"), "noext.jpg");
  assert.equal(jpegName(""), "picture.jpg");
});

test("the steps of attaching are worded, and what was read comes from the server's answer", () => {
  assert.equal(statusLabel("uploading"), "Uploading…");
  assert.equal(statusLabel("reading"), "Reading…");
  assert.equal(statusLabel("ready"), "Ready");
  assert.equal(statusLabel("nonsense"), "");
  assert.equal(describeRead({ kind: "image", pictures: 1 }), "Picture");
  assert.equal(describeRead(null), "");
});

test("a saved question names its pictures in its first words", () => {
  assert.deepEqual(parseAttached("[Attached: notes.jpg, shot.png] Solve Q2"), { names: ["notes.jpg", "shot.png"], text: "Solve Q2" });
  assert.deepEqual(parseAttached("Just a question"), { names: [], text: "Just a question" });
  assert.deepEqual(parseAttached("[Attached: broken"), { names: [], text: "[Attached: broken" });
  assert.deepEqual(parseAttached(undefined), { names: [], text: "" });
  assert.ok(ATTACHED_MARKER.startsWith("[Attached"));
});
