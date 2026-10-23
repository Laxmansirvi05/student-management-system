// Tests for the notes autosave engine (CC-07), run with Node's own test runner and no packages:
//   node --test src/test/js
// The engine is plain browser JavaScript; the server calls and the page events are replaced here.

import assert from "node:assert/strict";
import { afterEach, beforeEach, mock, test } from "node:test";
import { createAutosave, isTransient, retryDelay } from "../../main/resources/static/js/notes/autosave.js";

const settle = () => new Promise((resolve) => setImmediate(resolve));

async function advance(ms) {
  mock.timers.tick(ms);
  for (let i = 0; i < 5; i += 1) await settle();
}

function failure(status, retryAfter = null) {
  return Object.assign(new Error("failed"), { status, retryAfter });
}

/** A fake notes API that answers with the queued results in order, then succeeds. */
function fakeClient(...results) {
  const calls = [];
  const next = (kind, args) => {
    calls.push({ kind, ...args });
    const result = results.shift();
    if (result instanceof Error) return Promise.reject(result);
    return Promise.resolve(result ?? { id: 7, ...args.input });
  };
  return {
    calls,
    createNote: (input, options) => next("create", { input, options }),
    updateNote: (id, input, options) => next("update", { id, input, options }),
    deleteNote: (id) => next("delete", { id }),
  };
}

function fakePage() {
  let hide = () => {};
  return {
    watch: (callback) => {
      hide = callback;
      return () => {
        hide = () => {};
      };
    },
    hide: () => hide(),
  };
}

function autosave(client, extra = {}) {
  const statuses = [];
  const page = fakePage();
  const engine = createAutosave({
    note: { id: 7, title: "T", content: "<p>a</p>" },
    debounceMs: 800,
    client,
    watchPage: page.watch,
    onStatus: (status) => statuses.push(status),
    ...extra,
  });
  return { engine, statuses, page };
}

beforeEach(() => mock.timers.enable({ apis: ["setTimeout"] }));
afterEach(() => mock.timers.reset());

test("only passing failures are retried", () => {
  for (const status of [0, 408, 429, 500, 502, 503, 504]) assert.equal(isTransient({ status }), true, String(status));
  for (const status of [400, 401, 403, 404, 409, 413, 501]) assert.equal(isTransient({ status }), false, String(status));
});

test("Retry-After in seconds wins over the backoff, and an HTTP date falls back to it", () => {
  assert.equal(retryDelay({ retryAfter: "7" }, 0), 7000);
  assert.equal(retryDelay({ retryAfter: "999" }, 0), 60000);
  assert.equal(retryDelay({ retryAfter: "Wed, 21 Oct 2026 07:28:00 GMT" }, 2), 4000);
  assert.equal(retryDelay({}, 0), 1000);
  assert.equal(retryDelay({}, 99), 30000);
});

test("a save that fails for a passing reason is retried after Retry-After and then succeeds", async () => {
  const client = fakeClient(failure(429, "3"));
  const { engine, statuses } = autosave(client);
  engine.setContent("<p>b</p>");
  await advance(800);
  assert.equal(client.calls.length, 1);
  assert.equal(engine.status, "retrying");

  await advance(2999);
  assert.equal(client.calls.length, 1, "not before Retry-After");
  await advance(1);
  assert.equal(client.calls.length, 2);
  assert.equal(client.calls[1].input.content, "<p>b</p>");
  assert.equal(engine.status, "saved");
  assert.deepEqual(statuses, ["saving", "retrying", "saving", "saved"]);
});

test("the backoff grows, and gives up with an error after the last try", async () => {
  const client = fakeClient(...Array.from({ length: 7 }, () => failure(503)));
  const { engine } = autosave(client);
  engine.setContent("<p>b</p>");
  await advance(800);
  for (const wait of [1000, 2000, 4000, 8000, 16000, 30000]) {
    assert.equal(engine.status, "retrying");
    await advance(wait);
  }
  assert.equal(client.calls.length, 7);
  assert.equal(engine.status, "error");
  await advance(120000);
  assert.equal(client.calls.length, 7, "no more tries until the next edit");
});

test("a refused save is not retried", async () => {
  const client = fakeClient(failure(400));
  const { engine } = autosave(client);
  engine.setContent("<p>b</p>");
  await advance(800);
  await advance(60000);
  assert.equal(client.calls.length, 1);
  assert.equal(engine.status, "error");
});

test("a create that may have reached the server is not repeated, so no second note appears", async () => {
  const client = fakeClient(failure(0));
  const { engine } = autosave(client, { note: null });
  engine.setContent("<p>new</p>");
  await advance(800);
  await advance(60000);
  assert.deepEqual(client.calls.map((c) => c.kind), ["create"]);
  assert.equal(engine.status, "error");
});

test("a create the server refused for load is retried", async () => {
  const client = fakeClient(failure(503));
  const { engine } = autosave(client, { note: null });
  engine.setContent("<p>new</p>");
  await advance(800);
  await advance(1000);
  assert.deepEqual(client.calls.map((c) => c.kind), ["create", "create"]);
  assert.equal(engine.noteId, 7);
});

test("newer typing during a retry wait is saved once, and the old retry does not write again", async () => {
  const client = fakeClient(failure(503));
  const { engine } = autosave(client);
  engine.setContent("<p>old</p>");
  await advance(800);
  engine.setContent("<p>newer</p>");
  await advance(800);
  await advance(30000);
  const updates = client.calls.filter((c) => c.kind === "update");
  assert.deepEqual(updates.map((c) => c.input.content), ["<p>old</p>", "<p>newer</p>"]);
});

test("hiding the page sends unsaved text at once with keepalive, and nothing when all is saved", async () => {
  const client = fakeClient();
  const { engine, page } = autosave(client);
  page.hide();
  await advance(0);
  assert.equal(client.calls.length, 0, "nothing to save");

  engine.setContent("<p>typed</p>");
  page.hide();
  await advance(0);
  assert.equal(client.calls.length, 1);
  assert.equal(client.calls[0].options.keepalive, true);
  await advance(800);
  assert.equal(client.calls.length, 1, "the debounce does not save it a second time");
});

test("hiding the page during a retry wait sends the text now", async () => {
  const client = fakeClient(failure(503));
  const { engine, page } = autosave(client);
  engine.setContent("<p>b</p>");
  await advance(800);
  page.hide();
  await advance(0);
  assert.equal(client.calls.length, 2);
  assert.equal(client.calls[1].options.keepalive, true);
  assert.equal(engine.status, "saved");
});

test("flush saves pending text once, and does nothing when everything is saved", async () => {
  const client = fakeClient();
  const { engine } = autosave(client);
  await engine.flush();
  assert.equal(client.calls.length, 0);
  engine.setContent("<p>b</p>");
  await engine.flush();
  await advance(800);
  assert.equal(client.calls.length, 1);
});

test("a template draft closed untouched is still created, as before", async () => {
  const client = fakeClient();
  const { engine } = autosave(client, { note: null, draft: { title: "Lab", content: "<h2>Aim</h2>" } });
  await engine.flush();
  assert.deepEqual(client.calls.map((c) => c.kind), ["create"]);
  assert.equal(client.calls[0].input.title, "Lab");
});

test("dispose stops listening to the page", async () => {
  const client = fakeClient();
  const { engine, page } = autosave(client);
  engine.setContent("<p>b</p>");
  engine.dispose();
  page.hide();
  await advance(5000);
  assert.equal(client.calls.length, 0);
});
