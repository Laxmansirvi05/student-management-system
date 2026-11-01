import { api, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { cardHead, empty } from "./parts.js";
import { groupMessages, shownStyle } from "./robot/chat-groups.js";
import { renderRich } from "./robot/rich-render.js";

// The robot's Quick Assistant chats, on the AI Assistant page. They are kept apart from the study
// tools above: a quick chat is a short exchange, and the robot keeps only the newest six. Only this
// student can read them, and each can be deleted here.
//
// A saved answer is drawn by the same code as the robot's live answers (robot/rich-render.js), from
// the text exactly as it was saved, so headings, lists, code, tables and diagrams look as they did
// live. A question answered in both modes shows both, with a switch between the saved ones. Nothing
// is asked of the model here: this page only reads what is saved.

const host = document.getElementById("quick-history");
const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const MODES = [
  ["quick", "Quick"],
  ["explain", "Explanatory"],
];

let chats = [];
let openId = null;
let openMessages = [];
let loadFailed = false;
const chosen = new Map(); // question id -> the mode shown

async function toggle(id) {
  if (openId === id) {
    openId = null;
    openMessages = [];
    loadFailed = false;
    return draw();
  }
  openId = id;
  openMessages = [];
  loadFailed = false;
  chosen.clear();
  draw();
  try {
    openMessages = (await api.get(`/assistant/conversations/${id}/messages`)).messages;
  } catch (failure) {
    loadFailed = true;
    toast(messageFor(failure));
  }
  draw();
}

async function remove(chat) {
  try {
    await api.delete(`/assistant/conversations/${chat.id}`);
    chats = chats.filter((entry) => entry.id !== chat.id);
    if (openId === chat.id) {
      openId = null;
      openMessages = [];
    }
    toast("Quick chat deleted");
    draw();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

/** The switch between the saved modes of one question: a mode with no saved answer is not offered. */
function modeSwitch(group, shown) {
  const saved = MODES.filter(([value]) => group.answers[value]);
  if (saved.length < 2) return null;
  return h(
    "div.rb-answer__modes",
    { role: "group", "aria-label": "Answer mode of this question" },
    saved.map(([value, name]) =>
      h("button.rb-answer__mode", {
        type: "button",
        "aria-pressed": String(value === shown),
        onclick: () => {
          chosen.set(group.question.id, value);
          draw();
        },
      }, name),
    ),
  );
}

function conversation() {
  if (loadFailed) return h("p.row__meta", { role: "alert" }, "Could not load this chat. Try again.");
  if (openMessages.length === 0) return h("p.row__meta", "Loading messages…");
  return groupMessages(openMessages).map((group) => {
    const shown = shownStyle(group, chosen.get(group.question.id));
    const answer = group.answers[shown];
    const question = group.question.content.replace(/^\[Image\] /, "");
    return h(
      "div.quick-chat__group",
      h("p.quick-chat__message.quick-chat__message--user", group.question.content.startsWith("[Image] ") ? [icon("file"), " Picture · "] : null, question),
      answer ? h("div.quick-chat__answer.rb-rich", modeSwitch(group, shown), renderRich(answer.content)) : null,
    );
  });
}

function draw() {
  fill(
    host,
    h(
      "section.card",
      { "aria-label": "Quick assistant history" },
      cardHead("history", "Quick assistant history"),
      chats.length === 0
        ? empty("sparkles", "No quick chats yet", "Double-tap the robot on any page for a quick answer. Your chats appear here, and only you can read them.")
        : h(
            "ul.rows",
            chats.map((chat) =>
              h(
                "li.row.quick-chat",
                h(
                  "div.line",
                  h("button.quick-chat__title", { type: "button", "aria-expanded": String(openId === chat.id), onclick: () => toggle(chat.id) }, h("span.row__title", chat.title || "Untitled chat"), h("span.row__meta.num", dateFormat.format(new Date(chat.updatedAt)))),
                  h("button.button.button--quiet.button--small", { type: "button", "aria-label": `Delete ${chat.title || "quick chat"}`, onclick: () => remove(chat) }, icon("trash")),
                ),
                openId === chat.id ? h("div.quick-chat__messages", conversation()) : null,
              ),
            ),
          ),
    ),
  );
}

(async () => {
  try {
    chats = (await api.get("/assistant/conversations?source=quick")).conversations;
  } catch {
    chats = [];
  }
  draw();
})();
