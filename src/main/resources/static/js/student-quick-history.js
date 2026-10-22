import { api, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { cardHead, empty } from "./parts.js";

// The robot's Quick Assistant chats, on the AI Assistant page. They are kept apart from the study
// tools above: a quick chat is a short exchange, and the robot keeps only the newest six. Only this
// student can read them, and each can be deleted here. Messages are put on the page as plain text.

const host = document.getElementById("quick-history");
const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

let chats = [];
let openId = null;
let openMessages = [];

async function toggle(id) {
  if (openId === id) {
    openId = null;
    openMessages = [];
    return draw();
  }
  openId = id;
  openMessages = [];
  draw();
  try {
    openMessages = (await api.get(`/assistant/conversations/${id}/messages`)).messages;
  } catch (failure) {
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
                openId === chat.id
                  ? h(
                      "div.quick-chat__messages",
                      openMessages.length === 0 ? h("p.row__meta", "Loading messages…") : openMessages.map((message) => h("p.quick-chat__message" + (message.role === "user" ? ".quick-chat__message--user" : ""), message.content)),
                    )
                  : null,
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
