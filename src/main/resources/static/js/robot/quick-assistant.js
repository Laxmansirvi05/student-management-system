import { h } from "../dom.js";
import { api, messageFor } from "../api.js";
import { symbol } from "../notes/symbols.js";
import { createFloatingPanel } from "./floating-panel.js";

// The robot's double-tap Quick Assistant. It is not a second assistant: it asks the same AI provider
// through the same server code as the study tools, under the same daily limits, and its chats are
// kept as a short history only the student can read. The brevity of the answer is the model's
// instruction, not text cut off here.

const DEFAULT_SIZE = { width: 360, height: 440 };
const MESSAGE_MAX = 4000;

/** Options: anchor ({x, y, size}), onClose(). Returns { close }. */
export function openQuickAssistant({ anchor, onClose }) {
  let messages = [];
  let conversationId = null;
  let history = [];
  let historyOpen = false;
  let sending = false;
  let error = null;

  const transcript = h("div.rb-chat__log");
  const historyBox = h("div.rb-chat__history", { hidden: true });
  const input = h("textarea.rb-chat__input", {
    rows: "1",
    maxlength: String(MESSAGE_MAX),
    placeholder: "Ask something…",
    "aria-label": "Message the quick assistant",
    oninput: refresh,
    onkeydown: (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        send();
      }
    },
  });
  const sendButton = h("button.rb-chat__send", { type: "button", "aria-label": "Send", onclick: send }, symbol("send"));

  const newChat = h("button.rb-iconbtn", { type: "button", "aria-label": "New quick chat", title: "New chat", onclick: startNewChat }, symbol("add_comment"));
  const historyButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Quick chat history", "aria-expanded": "false", title: "History", onclick: () => ((historyOpen = !historyOpen), drawHistory()) }, symbol("history"));
  const closeButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Close quick assistant", onclick: close }, symbol("close"));
  const header = h("div.rb-panel__drag", symbol("drag_indicator"), h("h2.rb-panel__title.rb-panel__title--grow", "Quick Assistant"), newChat, historyButton, closeButton);

  const body = h("div.rb-chat", historyBox, transcript, h("div.rb-chat__compose", input, sendButton));
  const panel = createFloatingPanel({
    label: "Quick assistant",
    anchor,
    defaultSize: DEFAULT_SIZE,
    storageKey: "robot_quick_assistant_size_v1",
    placement: "assistant",
    header,
    body,
    backdropLevel: 9997,
    resizeLabel: "Resize quick assistant",
    onBackdrop: () => close(),
  });

  function refresh() {
    sendButton.disabled = !input.value.trim() || sending;
  }

  function drawLog() {
    transcript.replaceChildren();
    if (messages.length === 0 && !sending) {
      transcript.append(
        h("div.rb-chat__empty", symbol("smart_toy"), h("p", "Ask anything. Answers here are kept short. Open the AI Assistant for the full version.")),
      );
    }
    for (const message of messages) {
      transcript.append(h("div.rb-bubble" + (message.role === "user" ? ".rb-bubble--user" : ".rb-bubble--ai"), message.content));
    }
    if (sending) {
      transcript.append(h("div.rb-bubble.rb-bubble--ai", { "aria-live": "polite" }, h("span.rb-bubble__wait", "Thinking…")));
    }
    if (error) {
      transcript.append(h("p.rb-chat__error", { role: "alert" }, error));
    }
    transcript.scrollTo({ top: transcript.scrollHeight, behavior: "smooth" });
  }

  function drawHistory() {
    historyBox.hidden = !historyOpen;
    historyButton.setAttribute("aria-expanded", String(historyOpen));
    historyButton.classList.toggle("is-on", historyOpen);
    historyBox.replaceChildren();
    if (!historyOpen) return;
    if (history.length === 0) {
      historyBox.append(h("p.rb-chat__none", "No quick chats yet."));
      return;
    }
    for (const entry of history) {
      historyBox.append(
        h("button.rb-chat__entry" + (entry.id === conversationId ? ".is-current" : ""), { type: "button", onclick: () => openChat(entry.id) }, entry.title || "Untitled chat"),
      );
    }
  }

  async function loadHistory() {
    try {
      history = (await api.get("/assistant/conversations?source=quick")).conversations;
    } catch {
      history = [];
    }
    drawHistory();
  }

  async function openChat(id) {
    historyOpen = false;
    error = null;
    conversationId = id;
    drawHistory();
    try {
      messages = (await api.get(`/assistant/conversations/${id}/messages`)).messages;
    } catch {
      error = "Could not load that chat.";
    }
    drawLog();
  }

  function startNewChat() {
    conversationId = null;
    messages = [];
    error = null;
    historyOpen = false;
    drawHistory();
    drawLog();
    input.focus();
  }

  async function send() {
    const text = input.value.trim();
    if (!text || sending) return;
    input.value = "";
    error = null;
    sending = true;
    refresh();
    // The question shows at once; the server stores it with the answer, so a failed answer leaves nothing behind.
    messages = [...messages, { role: "user", content: text }];
    drawLog();
    try {
      const answer = await api.post("/assistant/chat", { conversationId, message: text, mode: "quick" });
      conversationId = answer.conversation.id;
      messages = [...messages.slice(0, -1), answer.question, answer.reply];
      history = [answer.conversation, ...history.filter((entry) => entry.id !== answer.conversation.id)].slice(0, 6);
    } catch (failure) {
      messages = messages.slice(0, -1);
      input.value = text;
      error = messageFor(failure);
    } finally {
      sending = false;
      refresh();
      drawLog();
      input.focus();
    }
  }

  let closed = false;
  function close() {
    if (closed) return;
    closed = true;
    document.removeEventListener("keydown", onKeydown);
    panel.destroy();
    onClose();
  }

  function onKeydown(event) {
    if (event.key !== "Escape" || document.querySelector("dialog[open]")) return;
    if (historyOpen) {
      historyOpen = false;
      drawHistory();
    } else {
      close();
    }
  }
  document.addEventListener("keydown", onKeydown);

  document.body.append(panel.backdrop, panel.element);
  drawLog();
  refresh();
  loadHistory();
  input.focus();
  return { close };
}
