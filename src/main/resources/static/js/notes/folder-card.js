import { h } from "../dom.js";
import { paletteFor } from "./folder-colors.js";
import { symbol } from "./symbols.js";

// A folder rendered with real depth: a back panel, the notes sitting inside it, and a front panel
// that tilts open on hover. Built from CSS perspective and transforms rather than a 3D renderer:
// every folder on screen would otherwise cost a WebGL context, and this is a browsing grid that
// needs to stay responsive at a hundred folders. Only transform, opacity and box-shadow animate,
// so nothing here triggers layout.

/**
 * How far each hovered preview card fans out. Index 0 sits centre-back, and the rest alternate
 * left and right so the stack opens like a hand of cards rather than drifting one way.
 */
const FAN = [
  { x: 0, y: -74, rotate: 0, delay: 0 },
  { x: -76, y: -60, rotate: -11, delay: 40 },
  { x: 76, y: -60, rotate: 11, delay: 40 },
  { x: -138, y: -34, rotate: -19, delay: 80 },
  { x: 138, y: -34, rotate: 19, delay: 80 },
];

const countText = (count) => `${count} ${count === 1 ? "note" : "notes"}`;

/** Options: folder, onOpen(folder), onContextMenu(folder, {x, y}). */
export function folderCard({ folder, onOpen, onContextMenu }) {
  const palette = paletteFor(folder.color);
  const previews = folder.previews.slice(0, FAN.length);
  let hovered = false;
  let opening = false;

  const stage = h("div.nt-folder__stage");
  const back = h("div.nt-folder__back");
  back.style.backgroundColor = palette.back;
  const tab = h("div.nt-folder__tab");
  tab.style.backgroundColor = palette.tab;

  const sheets = previews.map((preview, index) => {
    const sheet = h(
      "div.nt-folder__sheet",
      { "aria-hidden": "true" },
      preview.imageUrl
        ? h(
            "div.nt-folder__sheet-split",
            h("div.nt-folder__sheet-image"),
            h("div.nt-folder__sheet-text", h("p.nt-folder__sheet-title", preview.title || "Untitled"), h("p.nt-folder__sheet-snippet", preview.snippet)),
          )
        : h("div.nt-folder__sheet-text", h("p.nt-folder__sheet-title", preview.title || "Untitled"), h("p.nt-folder__sheet-snippet", preview.snippet)),
    );
    if (preview.imageUrl) {
      sheet.querySelector(".nt-folder__sheet-image").style.backgroundImage = `url("${preview.imageUrl}")`;
    }
    sheet.style.zIndex = String(5 - index);
    return sheet;
  });

  const front = h("div.nt-folder__front", symbol("folder"), h("span.nt-folder__count", String(folder.noteCount)));
  front.style.background = `linear-gradient(160deg, ${palette.frontFrom} 0%, ${palette.frontTo} 100%)`;
  front.style.color = palette.ink;
  front.querySelector(".material-symbols-outlined").textContent = folder.icon || "folder";
  front.querySelector(".material-symbols-outlined").classList.add("nt-folder__icon");

  stage.append(back, tab, ...sheets, front);

  function paint() {
    const lifted = hovered || opening;
    stage.style.transform = lifted ? "translateY(-10px) rotateX(-8deg)" : "rotateX(0deg)";
    stage.style.filter = lifted ? `drop-shadow(0 18px 28px ${palette.glow})` : "drop-shadow(0 6px 12px rgba(0,0,0,0.18))";
    front.style.transform = lifted ? "rotateX(-32deg)" : "rotateX(0deg)";
    sheets.forEach((sheet, index) => {
      const fan = FAN[index];
      sheet.style.transform = lifted
        ? `translateX(calc(-50% + ${fan.x}px)) translateY(${fan.y}px) rotate(${fan.rotate}deg) scale(1)`
        : "translateX(-50%) translateY(6px) rotate(0deg) scale(0.9)";
      sheet.style.opacity = lifted ? "1" : "0";
      sheet.style.transitionDelay = `${lifted ? fan.delay : 0}ms`;
    });
    card.style.transform = opening ? "scale(1.06)" : "";
  }

  function open() {
    if (opening) return;
    opening = true;
    paint();
    // Let the open animation play before the view swaps, so the folder is seen opening rather than
    // the grid simply blinking out.
    setTimeout(() => onOpen(folder), 220);
  }

  const card = h(
    "div.nt-folder",
    {
      role: "button",
      tabindex: "0",
      "aria-label": `${folder.name}, ${countText(folder.noteCount)}`,
      onclick: open,
      onkeydown: (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          open();
        }
        if (event.key === "ContextMenu" || (event.shiftKey && event.key === "F10")) {
          event.preventDefault();
          const box = event.target.getBoundingClientRect();
          onContextMenu(folder, { x: box.left + box.width / 2, y: box.top + box.height / 2 });
        }
      },
      oncontextmenu: (event) => {
        event.preventDefault();
        onContextMenu(folder, { x: event.clientX, y: event.clientY });
      },
      onpointerenter: () => ((hovered = true), paint()),
      onpointerleave: () => ((hovered = false), paint()),
      onfocus: () => ((hovered = true), paint()),
      onblur: () => ((hovered = false), paint()),
    },
    stage,
    h("div.nt-folder__label", h("p.nt-folder__name", folder.name), h("p.nt-folder__sub", countText(folder.noteCount))),
  );
  paint();
  return card;
}
