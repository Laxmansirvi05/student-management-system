import { h, icon } from "../dom.js";
import { api, messageFor } from "../api.js";
import { symbol } from "../notes/symbols.js";
import { createFloatingPanel } from "./floating-panel.js";
import { renderRich } from "./rich-render.js";

// The robot's double-tap Quick Assistant. It is not a second assistant: it asks the same AI provider
// through the same server code as the study tools, under the same daily limits, and its chats are
// kept as a short history only the student can read. The brevity of the answer is the model's
// instruction, not text cut off here.
//
// Two answer modes (Quick Answer, the default, and Explanatory Answer) are chosen above the chat and
// travel with each message as "style". A question may come with a JPG or PNG picture: chosen from
// the device, captured from a screen, window or tab (the browser's own picker; the page cannot
// choose a region for the user), or pasted. The picture is sent with the message, redrawn and read by
// the server, and is not stored. Explanatory answers are drawn by rich-render.js.

const DEFAULT_SIZE = { width: 360, height: 440 };
const MESSAGE_MAX = 4000;
const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const CAPTURE_SIDE = 1568;
const IMAGE_MARKER = "[Image] ";
const MODES = [
  ["quick", "Quick Answer"],
  ["explain", "Explanatory Answer"],
];
const HINTS = {
  quick: "Ask anything. Quick answers are short. Switch to Explanatory Answer for the full explanation.",
  explain: "Ask anything. Explanatory answers are detailed, with steps, examples and diagrams.",
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
  let attachment = null; // { file, url }
  let capturing = false;
  let pendingPicture = false;

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
  const fileInput = h("input", { type: "file", accept: "image/jpeg,image/png,.jpg,.jpeg,.png", hidden: true, "aria-hidden": "true", tabindex: "-1", onchange: picked });
  const attachButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Attach a JPG or PNG picture", title: "Attach a picture", onclick: () => fileInput.click() }, symbol("image"));
  const canCapture = typeof navigator !== "undefined" && !!navigator.mediaDevices?.getDisplayMedia && window.isSecureContext;
  const captureButton = canCapture ? h("button.rb-iconbtn", { type: "button", "aria-label": "Capture a screen, window or tab", title: "Capture the screen", onclick: capture }, icon("monitor")) : null;
  const sendButton = h("button.rb-chat__send", { type: "button", "aria-label": "Send", onclick: send }, symbol("send"));

  const newChat = h("button.rb-iconbtn", { type: "button", "aria-label": "New quick chat", title: "New chat", onclick: startNewChat }, symbol("add_comment"));
  const historyButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Quick chat history", "aria-expanded": "false", title: "History", onclick: () => ((historyOpen = !historyOpen), drawHistory()) }, symbol("history"));
  const closeButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Close quick assistant", onclick: close }, symbol("close"));
  const header = h("div.rb-panel__drag", symbol("drag_indicator"), h("h2.rb-panel__title.rb-panel__title--grow", "Quick Assistant"), newChat, historyButton, closeButton);

  const modeButtons = MODES.map(([value, name]) =>
    h("button.rb-mode__option", { type: "button", "data-mode": value, "aria-pressed": String(value === style), onclick: () => setStyle(value) }, name),
  );
  const modeBar = h("div.rb-mode", { role: "group", "aria-label": "Answer mode" }, modeButtons);
  const previewBox = h("div.rb-attach", { hidden: true });
  const body = h(
    "div.rb-chat",
    historyBox,
    modeBar,
    transcript,
    previewBox,
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
    sendButton.disabled = (!input.value.trim() && !attachment) || sending || capturing;
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

  // --- the picture ---------------------------------------------------------------------------

  function setAttachment(file) {
    if (!file) return;
    if (!/^image\/(jpeg|png)$/.test(file.type)) {
      error = "Attach a JPG or PNG picture.";
      drawLog();
      return;
    }
    if (file.size > IMAGE_MAX_BYTES) {
      error = "The picture is larger than 5 MB.";
      drawLog();
      return;
    }
    clearAttachment();
    error = null;
    attachment = { file, url: URL.createObjectURL(file) };
    drawAttachment();
    drawLog();
    refresh();
  }

  function clearAttachment() {
    if (attachment) URL.revokeObjectURL(attachment.url);
    attachment = null;
    drawAttachment();
    refresh();
  }

  function drawAttachment() {
    previewBox.hidden = !attachment;
    previewBox.replaceChildren();
    if (!attachment) return;
    previewBox.append(
      h("img.rb-attach__thumb", { src: attachment.url, alt: "The attached picture" }),
      h("span.rb-attach__name", attachment.file.name || "Picture"),
      h("button.rb-iconbtn", { type: "button", "aria-label": "Remove the picture", title: "Remove", onclick: () => (clearAttachment(), input.focus()) }, symbol("close")),
    );
  }

  function picked() {
    setAttachment(fileInput.files?.[0]);
    fileInput.value = "";
  }

  /** Ctrl+V or Cmd+V with a copied picture in the clipboard attaches it; text pastes as usual. */
  function pasted(event) {
    const files = [...(event.clipboardData?.files ?? [])].filter((file) => file.type.startsWith("image/"));
    if (!files.length) return;
    event.preventDefault();
    setAttachment(files[0]);
  }

  /**
   * Captures a screen, window or tab with the browser's own picker, as one still picture. The page
   * cannot choose an area of the screen: the student picks what to share, the browser asks
   * permission, and the sharing stops as soon as the picture is taken.
   */
  async function capture() {
    if (capturing || sending) return;
    capturing = true;
    error = null;
    captureButton.disabled = true;
    refresh();
    let stream = null;
    try {
      stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      const video = h("video", { muted: true, playsinline: true });
      video.srcObject = stream;
      await video.play();
      await new Promise((resolve) => setTimeout(resolve, 350)); // let the first full frame arrive
      const scale = Math.min(1, CAPTURE_SIDE / Math.max(video.videoWidth, video.videoHeight));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
      canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
      canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
      let blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
      if (blob && blob.size > IMAGE_MAX_BYTES - 262144) blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.9));
      if (!blob) throw new Error("empty");
      const name = blob.type === "image/png" ? "screen-capture.png" : "screen-capture.jpg";
      setAttachment(new File([blob], name, { type: blob.type }));
    } catch (failure) {
      // Closing the picker is not an error.
      error = failure?.name === "NotAllowedError" || failure?.name === "AbortError" ? null : "The screen could not be captured in this browser.";
      drawLog();
    } finally {
      stream?.getTracks().forEach((track) => track.stop());
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
    for (const message of messages) {
      if (message.role === "user") {
        const pictured = message.image || message.content.startsWith(IMAGE_MARKER);
        const text = message.content.startsWith(IMAGE_MARKER) ? message.content.slice(IMAGE_MARKER.length) : message.content;
        transcript.append(
          h(
            "div.rb-bubble.rb-bubble--user",
            pictured ? (message.image ? h("img.rb-bubble__image", { src: message.image, alt: "Your picture" }) : h("span.rb-bubble__tag", symbol("image"), "Picture")) : null,
            text,
          ),
        );
      } else {
        transcript.append(h("div.rb-bubble.rb-bubble--ai.rb-bubble--rich", renderRich(message.content)));
      }
    }
    if (sending) {
      const wait = pendingPicture ? "Reading the picture…" : style === "explain" ? "Writing a detailed explanation…" : "Thinking…";
      transcript.append(h("div.rb-bubble.rb-bubble--ai", { "aria-live": "polite" }, h("span.rb-bubble__wait", wait)));
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
    clearAttachment();
    drawHistory();
    drawLog();
    input.focus();
  }

  async function send() {
    const text = input.value.trim();
    if ((!text && !attachment) || sending || capturing) return;
    input.value = "";
    error = null;
    sending = true;
    const sent = attachment;
    // The picture stays on screen with its question; the preview strip is cleared at once.
    attachment = null;
    drawAttachment();
    pendingPicture = !!sent;
    refresh();
    // The question shows at once; the server stores it with the answer, so a failed answer leaves nothing behind.
    messages = [...messages, { role: "user", content: text, image: sent?.url ?? null }];
    drawLog();
    try {
      const details = { conversationId, message: text, mode: "quick", style };
      let answer;
      if (sent) {
        const form = new FormData();
        form.append("metadata", new Blob([JSON.stringify(details)], { type: "application/json" }));
        form.append("image", sent.file, sent.file.name);
        answer = await api.postForm("/assistant/chat", form);
      } else {
        answer = await api.post("/assistant/chat", details);
      }
      conversationId = answer.conversation.id;
      messages = [...messages.slice(0, -1), { ...answer.question, image: sent?.url ?? null }, answer.reply];
      history = [answer.conversation, ...history.filter((entry) => entry.id !== answer.conversation.id)].slice(0, 6);
    } catch (failure) {
      // As in the original, the question stays on screen and the box stays empty; the error is shown below it.
      // The picture comes back to the preview strip, so the question can be sent again.
      error = messageFor(failure);
      if (sent) {
        attachment = sent;
        messages = messages.map((message, index) => (index === messages.length - 1 && message.image === sent.url ? { ...message, image: null } : message));
        drawAttachment();
      }
    } finally {
      sending = false;
      pendingPicture = false;
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
    if (attachment) URL.revokeObjectURL(attachment.url);
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
