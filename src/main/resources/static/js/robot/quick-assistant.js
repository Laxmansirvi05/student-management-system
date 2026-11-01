import { h, icon } from "../dom.js";
import { api, messageFor } from "../api.js";
import { symbol } from "../notes/symbols.js";
import { createFloatingPanel } from "./floating-panel.js";
import { groupMessages, lastStyle, shownStyle } from "./chat-groups.js";
import { renderRich } from "./rich-render.js";
import { selectArea } from "./area-select.js";
import { snapshotPage } from "./page-snapshot.js";
import { createAttachmentTray } from "./attachment-tray.js";
import { ATTACHED_MARKER, kindOf, parseAttached } from "./attachment-rules.js";

// The robot's double-tap Quick Assistant. It is not a second assistant: it asks the same AI provider
// through the same server code as the study tools, under the same daily limits, and its chats are
// kept as a short history only the student can read. The brevity of the answer is the model's
// instruction, not text cut off here.
//
// Two answer modes (Quick Answer, the default, and Explanatory Answer) are chosen above the chat and
// travel with each message as "style". A question may come with files (ADR 0019): pictures (JPG,
// PNG, WEBP), PDFs and Word documents, chosen from the device, pasted, or captured as an area of this
// page (dragged out like Cmd+Shift+4, drawn by page-snapshot.js; a web page cannot read the rest of
// the screen). Each file is sent to the server when it is attached and read there; it counts as
// Ready only when the server answers with what it found (attachment-tray.js). The files stay with
// the chat for follow-up questions. They are kept in the server's memory for a short time, not
// stored. Explanatory answers are drawn by rich-render.js.

const DEFAULT_SIZE = { width: 360, height: 440 };
const MESSAGE_MAX = 4000;
const IMAGE_MARKER = "[Image] ";
const MODES = [
  ["quick", "Quick Answer"],
  ["explain", "Explanatory Answer"],
];
const HINTS = {
  quick: "Ask anything. Quick answers are short. Attach pictures, PDFs or Word files to ask about them.",
  explain: "Ask anything. Explanatory answers are detailed, with steps, examples and diagrams. Attach pictures, PDFs or Word files to ask about them.",
};

/** Options: anchor ({x, y, size}), onClose(). Returns { close }. */
export function openQuickAssistant({ anchor, onClose }) {
  let messages = [];
  let conversationId = null;
  let history = [];
  let historyOpen = false;
  let sending = false;
  let error = null;
  let style = "quick";
  let capturing = false;
  let pendingFiles = false;
  const chosenStyle = new Map(); // question id -> the mode shown for it
  let generating = null; // { questionId, style } while the other mode of a question is being written

  const transcript = h("div.rb-chat__log");
  const historyBox = h("div.rb-chat__history", { hidden: true });
  const input = h("textarea.rb-chat__input", {
    rows: "1",
    maxlength: String(MESSAGE_MAX),
    placeholder: "Ask something…",
    "aria-label": "Message the quick assistant",
    oninput: refresh,
    onpaste: pasted,
    onkeydown: (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        send();
      }
    },
  });
  const fileInput = h("input", {
    type: "file",
    multiple: true,
    accept: "image/jpeg,image/png,image/webp,application/pdf,.jpg,.jpeg,.png,.webp,.pdf,.docx",
    hidden: true,
    "aria-hidden": "true",
    tabindex: "-1",
    onchange: picked,
  });
  const attachButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Attach pictures, PDFs or Word documents", title: "Attach pictures, PDFs or Word documents", onclick: () => fileInput.click() }, symbol("image"));
  const captureButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Capture an area of the page", title: "Capture an area of the page", onclick: () => capture() }, icon("monitor"));
  const sendButton = h("button.rb-chat__send", { type: "button", "aria-label": "Send", onclick: send }, symbol("send"));

  const newChat = h("button.rb-iconbtn", { type: "button", "aria-label": "New quick chat", title: "New chat", onclick: startNewChat }, symbol("add_comment"));
  const historyButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Quick chat history", "aria-expanded": "false", title: "History", onclick: () => ((historyOpen = !historyOpen), drawHistory()) }, symbol("history"));
  const closeButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Close quick assistant", onclick: close }, symbol("close"));
  const header = h("div.rb-panel__drag", symbol("drag_indicator"), h("h2.rb-panel__title.rb-panel__title--grow", "Quick Assistant"), newChat, historyButton, closeButton);

  const modeButtons = MODES.map(([value, name]) =>
    h("button.rb-mode__option", { type: "button", "data-mode": value, "aria-pressed": String(value === style), onclick: () => setStyle(value) }, name),
  );
  const modeBar = h("div.rb-mode", { role: "group", "aria-label": "Answer mode" }, modeButtons);
  const tray = createAttachmentTray({
    onChange: () => refresh(),
    onNotice: (text) => {
      error = text;
      drawLog();
    },
    onRecapture: (key) => capture(key),
  });
  const body = h(
    "div.rb-chat",
    historyBox,
    modeBar,
    transcript,
    tray.element,
    h("div.rb-chat__compose", attachButton, captureButton, fileInput, input, sendButton),
  );
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
    sendButton.disabled = (!input.value.trim() && tray.ids().length === 0) || sending || capturing || tray.busy();
  }

  /** Switches the answer mode. Only the mode changes: the chat, the box and the picture stay. */
  function setStyle(value) {
    if (value === style) return;
    style = value;
    for (const button of modeButtons) {
      const on = button.dataset.mode === style;
      button.setAttribute("aria-pressed", String(on));
      button.classList.toggle("is-on", on);
    }
    if (messages.length === 0 && !sending) drawLog();
  }

  // --- the files -----------------------------------------------------------------------------

  /** Attaches each chosen file; the tray reads them and shows how far each one has got. */
  function picked() {
    error = null;
    for (const file of fileInput.files ?? []) tray.add(file);
    fileInput.value = "";
    drawLog();
  }

  /** Ctrl+V or Cmd+V with copied files or a copied picture attaches them; text pastes as usual. */
  function pasted(event) {
    const files = [...(event.clipboardData?.files ?? [])].filter((file) => kindOf(file));
    if (!files.length) return;
    event.preventDefault();
    error = null;
    for (const file of files) tray.add(file);
    drawLog();
  }

  /** The parts of the page that must not be in the picture: the assistant itself and the robot. */
  const leftOut = () => [panel.element, panel.backdrop, ...document.querySelectorAll(".rb-robot")];

  /**
   * Captures an area of the visible page. The student drags a rectangle (area-select.js, like
   * Cmd+Shift+4); the assistant and the robot are hidden meanwhile so the page behind them can be
   * chosen, and are left out of the picture. A page cannot read pixels outside itself, so this never
   * shows the browser's sharing dialog and never captures the desktop or another window.
   */
  async function capture(replaceKey = null) {
    if (capturing || sending) return;
    capturing = true;
    error = null;
    captureButton.disabled = true;
    refresh();
    const hidden = leftOut();
    const before = hidden.map((element) => element.style.visibility);
    const hide = (on) => hidden.forEach((element, index) => (element.style.visibility = on ? "hidden" : before[index]));
    try {
      hide(true);
      const rect = await selectArea();
      if (!rect) return; // cancelled
      const blob = await snapshotPage({ rect, exclude: (element) => hidden.includes(element) });
      const name = blob.type === "image/png" ? "page-capture.png" : "page-capture.jpg";
      hide(false);
      error = null;
      if (replaceKey !== null) tray.remove(replaceKey);
      tray.add(new File([blob], name, { type: blob.type }), { captured: true });
      drawLog();
    } catch {
      error = "The page area could not be captured in this browser. Attach a picture instead.";
      drawLog();
    } finally {
      hide(false);
      capturing = false;
      captureButton.disabled = false;
      refresh();
      input.focus();
    }
  }

  function drawLog() {
    transcript.replaceChildren();
    if (messages.length === 0 && !sending) {
      transcript.append(
        h("div.rb-chat__empty", symbol("smart_toy"), h("p", HINTS[style])),
      );
    }
    for (const group of groupMessages(messages)) {
      const message = group.question;
      transcript.append(userBubble(message));
      const shown = shownStyle(group, chosenStyle.get(message.id));
      const waitingHere = generating && generating.questionId === message.id;
      if (waitingHere) {
        transcript.append(h("div.rb-bubble.rb-bubble--ai", { "aria-live": "polite" }, answerModes(group, generating.style), h("span.rb-bubble__wait", generating.style === "explain" ? "Writing a detailed explanation…" : "Writing a short answer…")));
      } else if (group.answers[shown]) {
        transcript.append(h("div.rb-bubble.rb-bubble--ai.rb-bubble--rich", answerModes(group, shown), renderRich(group.answers[shown].content)));
      }
    }
    if (sending && !generating) {
      const wait = pendingFiles ? "Reading your files…" : style === "explain" ? "Writing a detailed explanation…" : "Thinking…";
      transcript.append(h("div.rb-bubble.rb-bubble--ai", { "aria-live": "polite" }, h("span.rb-bubble__wait", wait)));
    }
    if (error) {
      transcript.append(h("p.rb-chat__error", { role: "alert" }, error));
    }
    transcript.scrollTo({ top: transcript.scrollHeight, behavior: "smooth" });
  }

  /**
   * A question as the student sees it: the files that came with it (a picture shows itself, any
   * other file its name), then the words. A saved question names its files in its first words
   * ("[Attached: a.pdf] "); a live one also has the files themselves.
   */
  function userBubble(message) {
    const picture = message.content.startsWith(IMAGE_MARKER);
    const { names, text: afterFiles } = parseAttached(message.content);
    const text = picture ? message.content.slice(IMAGE_MARKER.length) : afterFiles;
    const tags = [];
    if (message.image || picture) {
      tags.push(message.image ? h("img.rb-bubble__image", { src: message.image, alt: "Your picture" }) : h("span.rb-bubble__tag", symbol("image"), "Picture"));
    }
    const live = message.files ?? [];
    for (const file of live) {
      tags.push(file.url ? h("img.rb-bubble__image", { src: file.url, alt: file.name, title: file.name }) : h("span.rb-bubble__tag", icon("file"), file.name));
    }
    if (!live.length) for (const name of names) tags.push(h("span.rb-bubble__tag", icon("file"), name));
    return h("div.rb-bubble.rb-bubble--user", tags.length ? h("div.rb-bubble__files", tags) : null, text);
  }

  /**
   * The two modes of one saved question, as a small switch above its answer. The mode shown is
   * pressed. A mode already saved is shown from the saved text, with no call to the model; a mode
   * not yet written is written once and then kept. A question that came with a picture cannot get
   * a second mode, because the picture is not kept.
   */
  function answerModes(group, shown) {
    const id = group.question.id;
    if (id == null) return null;
    const pictured = group.question.content.startsWith(IMAGE_MARKER) || group.question.content.startsWith(ATTACHED_MARKER);
    return h(
      "div.rb-answer__modes",
      { role: "group", "aria-label": "Answer mode of this question" },
      MODES.map(([value, name]) => {
        const saved = !!group.answers[value];
        const blocked = !saved && (pictured || sending || capturing);
        return h(
          "button.rb-answer__mode" + (saved ? "" : ".is-empty"),
          {
            type: "button",
            "aria-pressed": String(value === shown),
            disabled: blocked,
            title: saved ? name : pictured ? "Ask again with the files to get this mode" : `Write the ${name.toLowerCase()}`,
            onclick: () => showMode(group, value),
          },
          name.replace(" Answer", ""),
        );
      }),
    );
  }

  async function showMode(group, value) {
    const id = group.question.id;
    if (group.answers[value]) {
      chosenStyle.set(id, value);
      drawLog();
      return;
    }
    if (sending || !conversationId) return;
    error = null;
    sending = true;
    generating = { questionId: id, style: value };
    refresh();
    drawLog();
    try {
      const answer = await api.post("/assistant/chat", { conversationId, questionId: id, mode: "quick", style: value });
      if (!messages.some((message) => message.id === answer.reply.id)) messages = [...messages, answer.reply];
      chosenStyle.set(id, value);
    } catch (failure) {
      error = messageFor(failure);
    } finally {
      sending = false;
      generating = null;
      refresh();
      drawLog();
    }
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
    chosenStyle.clear();
    try {
      messages = (await api.get(`/assistant/conversations/${id}/messages`)).messages;
      // The mode of the saved answers comes back with the chat.
      const saved = lastStyle(messages);
      if (saved) setStyle(saved);
    } catch {
      error = "Could not load that chat.";
    }
    drawLog();
  }

  function startNewChat() {
    conversationId = null;
    messages = [];
    chosenStyle.clear();
    error = null;
    historyOpen = false;
    tray.clear();
    drawHistory();
    drawLog();
    input.focus();
  }

  async function send() {
    const text = input.value.trim();
    const ids = tray.ids();
    if ((!text && ids.length === 0) || sending || capturing || tray.busy()) return;
    if (tray.problem()) {
      error = "One of the files could not be read. Retry it or remove it, then send.";
      drawLog();
      return;
    }
    input.value = "";
    error = null;
    sending = true;
    const sentFiles = tray.files();
    pendingFiles = sentFiles.length > 0;
    refresh();
    // The question shows at once; the server stores it with the answer, so a failed answer leaves nothing behind.
    messages = [...messages, { role: "user", content: text, files: sentFiles }];
    drawLog();
    try {
      const details = { conversationId, message: text, mode: "quick", style, attachmentIds: ids };
      const answer = await api.post("/assistant/chat", details);
      conversationId = answer.conversation.id;
      messages = [...messages.slice(0, -1), { ...answer.question, files: sentFiles }, answer.reply];
      chosenStyle.set(answer.question.id, answer.reply.style === "explain" ? "explain" : "quick");
      history = [answer.conversation, ...history.filter((entry) => entry.id !== answer.conversation.id)].slice(0, 6);
    } catch (failure) {
      // The question stays on screen and the box stays empty; the error is shown below it. The files
      // stay attached, so the question can be sent again.
      error = messageFor(failure);
      if (failure?.status === 409 && /no longer available/i.test(failure.message)) tray.expireAll();
    } finally {
      sending = false;
      pendingFiles = false;
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
    tray.clear();
    for (const message of messages) if (message.image) URL.revokeObjectURL(message.image);
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
