// Draws a picture of an area of the page you are looking at, with no library and without the
// browser's screen-sharing dialog.
//
// What a web page can and cannot do: it cannot read the pixels of the screen, a window or another tab
// (only the browser's own screen-sharing picker can, and that is the dialog this tool avoids). It can,
// however, draw itself: this copies the visible part of the page (its elements, with their computed
// styles, the fonts and the icon sprite embedded) into an SVG picture, lets the browser paint that,
// and cuts the chosen area out of it. So it captures this page only, never the desktop or other
// windows, and it is an accurate redraw rather than a photograph: things the page cannot hand over
// (video, frames, pictures from other sites) are left blank.
//
// The picture is sent only if the student sends the message, like any attached picture.

const SVG_NS = "http://www.w3.org/2000/svg";
export const MAX_SIDE = 1568;
const MAX_BYTES = 5 * 1024 * 1024;

const SKIPPED_TAGS = new Set(["script", "noscript", "style", "link", "meta", "template", "iframe", "video", "audio", "object", "embed", "head", "title", "base"]);
// SVG elements that draw nothing themselves but are looked up by others; they stay even though the
// browser reports them as display: none.
const LEAF_TAGS = new Set(["canvas", "img", "input", "textarea"]);
const SVG_DEFINITIONS = new Set(["defs", "marker", "symbol", "clippath", "mask", "pattern", "lineargradient", "radialgradient", "filter", "stop", "title", "desc", "metadata"]);

// Properties that children inherit: copied only when they differ from the parent's, which keeps the
// picture small.
const INHERITED = [
  "color", "direction", "font-family", "font-size", "font-style", "font-variant", "font-weight", "font-stretch", "font-kerning", "font-feature-settings", "font-variation-settings", "font-optical-sizing", "font-synthesis", "font-variant-ligatures", "font-variant-numeric", "font-size-adjust", "text-rendering", "hyphens",
  "letter-spacing", "line-height", "list-style-type", "list-style-position", "text-align", "text-indent", "text-transform", "text-shadow", "text-wrap", "white-space",
  "word-break", "word-spacing", "overflow-wrap", "tab-size", "visibility", "quotes", "writing-mode", "border-collapse", "border-spacing",
  "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset",
  "marker-start", "marker-mid", "marker-end", "text-anchor", "paint-order", "shape-rendering",
];

// Properties that do not inherit, each with the value that means "not set" (skipped).
const OWN = {
  display: null, position: "static", top: "auto", right: "auto", bottom: "auto", left: "auto", "z-index": "auto", float: "none", clear: "none",
  width: "auto", height: "auto", "min-width": "0px", "min-height": "0px", "max-width": "none", "max-height": "none", "box-sizing": "content-box",
  "margin-top": "0px", "margin-right": "0px", "margin-bottom": "0px", "margin-left": "0px",
  "padding-top": "0px", "padding-right": "0px", "padding-bottom": "0px", "padding-left": "0px",
  "border-top-left-radius": "0px", "border-top-right-radius": "0px", "border-bottom-right-radius": "0px", "border-bottom-left-radius": "0px",
  "overflow-x": "visible", "overflow-y": "visible", opacity: "1", transform: "none",
  "background-color": "rgba(0, 0, 0, 0)", "background-image": "none", "background-position": "0% 0%", "background-size": "auto", "background-repeat": "repeat",
  "box-shadow": "none", filter: "none", "backdrop-filter": "none", "-webkit-backdrop-filter": "none", "mix-blend-mode": "normal", "clip-path": "none",
  "flex-direction": "row", "flex-wrap": "nowrap", "flex-grow": "0", "flex-shrink": "1", "flex-basis": "auto", order: "0",
  "justify-content": "normal", "align-items": "normal", "align-self": "auto", "align-content": "normal", "justify-items": "legacy", "justify-self": "auto",
  "row-gap": "normal", "column-gap": "normal",
  "grid-template-columns": "none", "grid-template-rows": "none", "grid-auto-flow": "row", "grid-auto-columns": "auto", "grid-auto-rows": "auto",
  "grid-column-start": "auto", "grid-column-end": "auto", "grid-row-start": "auto", "grid-row-end": "auto",
  "text-decoration-line": "none", "text-overflow": "clip", "vertical-align": "baseline", "object-fit": "fill", "aspect-ratio": "auto",
  appearance: "none", "scrollbar-width": "auto", "scrollbar-color": "auto", "-webkit-line-clamp": "none", "-webkit-box-orient": "horizontal", "table-layout": "auto", "stop-color": "rgb(0, 0, 0)", "stop-opacity": "1", "dominant-baseline": "auto",
};
const SIDES = ["top", "right", "bottom", "left"];
// A side's border is copied only when it has a style.
const BORDER_PARTS = ["width", "style", "color"];
// What an element inside an <svg> needs: the painting, not the box.
const SVG_OWN = ["opacity", "stop-color", "stop-opacity", "clip-path", "filter", "dominant-baseline", "display"];

const sprites = new Map();
let fontCss = null;

// ----------------------------------------------------------------------------- pure helpers

/** How much to scale the picture of an area: the screen's pixel density, but never past MAX_SIDE. */
export function outputScale(rect, pixelRatio, maxSide = MAX_SIDE) {
  const wanted = Math.max(Number(pixelRatio) || 1, 1);
  const limit = maxSide / Math.max(rect.width, rect.height, 1);
  return Math.min(wanted, limit);
}

/** The pixel size of the picture of an area. */
export function outputSize(rect, scale) {
  return { width: Math.max(1, Math.round(rect.width * scale)), height: Math.max(1, Math.round(rect.height * scale)) };
}

function xmlSafe(text) {
  // eslint-disable-next-line no-control-regex
  return text.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F￾￿]/g, "");
}

function toBase64(text) {
  const bytes = new TextEncoder().encode(text);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

function bufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

// ----------------------------------------------------------------------------- shared downloads

/** An icon sprite file, as a map of id to <symbol>, fetched once per file. */
async function loadSprite(file) {
  if (sprites.has(file)) return sprites.get(file);
  const symbols = new Map();
  sprites.set(file, symbols);
  try {
    const response = await fetch(file, { credentials: "same-origin" });
    if (response.ok) {
      const parsed = new DOMParser().parseFromString(await response.text(), "image/svg+xml");
      for (const symbol of parsed.querySelectorAll("symbol[id]")) symbols.set(symbol.getAttribute("id"), symbol);
    }
  } catch {
    // Without the sprite its icons are simply left out.
  }
  return symbols;
}

const hrefOf = (use) => use.getAttribute("href") || use.getAttribute("xlink:href") || "";

/** The page's own @font-face rules with each font file embedded, as CSS text, built once. */
async function loadFontCss() {
  if (fontCss !== null) return fontCss;
  const rules = [];
  for (const sheet of document.styleSheets) {
    let list;
    try {
      list = sheet.cssRules;
    } catch {
      continue; // a sheet from another site cannot be read
    }
    for (const rule of list) {
      if (rule.type !== CSSRule.FONT_FACE_RULE) continue;
      const source = /url\(["']?([^"')]+)["']?\)/.exec(rule.style.getPropertyValue("src"));
      if (!source) continue;
      try {
        const response = await fetch(new URL(source[1], sheet.href || location.href), { credentials: "same-origin" });
        if (!response.ok) continue;
        const data = bufferToBase64(await response.arrayBuffer());
        const style = rule.style;
        rules.push(
          `@font-face{font-family:${style.getPropertyValue("font-family")};font-style:${style.getPropertyValue("font-style") || "normal"};` +
            `font-weight:${style.getPropertyValue("font-weight") || "normal"};src:url(data:font/woff2;base64,${data}) format("woff2")}`,
        );
      } catch {
        // A font that cannot be fetched falls back to the next one in the stack.
      }
    }
  }
  fontCss = rules.join("\n");
  return fontCss;
}

async function dataUrlOf(url) {
  const response = await fetch(url, { credentials: "same-origin" });
  if (!response.ok) throw new Error("not found");
  const blob = await response.blob();
  const type = blob.type || "application/octet-stream";
  return `data:${type};base64,${bufferToBase64(await blob.arrayBuffer())}`;
}

// ----------------------------------------------------------------------------- copying the page

function isSvgNode(element) {
  return element.namespaceURI === SVG_NS;
}

function setProp(copy, name, value) {
  copy.style.setProperty(name, value);
}

/**
 * Writes the computed styles of an element onto its copy. Every property is written, even one that
 * looks unset: the browser's own default styles (a button's border and font, a link's colour, a
 * heading's size) would otherwise come back on the copy.
 */
function copyStyle(copy, style, inSvg) {
  const names = inSvg ? [...INHERITED, ...SVG_OWN] : [...INHERITED, ...Object.keys(OWN)];
  for (const name of names) {
    const value = style.getPropertyValue(name);
    if (!value) continue;
    // Inside a drawing only a hidden element needs its display written.
    if (inSvg && name === "display" && value !== "none") continue;
    setProp(copy, name, value);
  }
  if (inSvg) return;
  for (const side of SIDES) {
    for (const part of BORDER_PARTS) setProp(copy, `border-${side}-${part}`, style.getPropertyValue(`border-${side}-${part}`));
  }
  setProp(copy, "transform-origin", style.getPropertyValue("transform-origin"));
  for (const part of ["color", "style", "thickness"]) setProp(copy, `text-decoration-${part}`, style.getPropertyValue(`text-decoration-${part}`));
}

/** The ::before or ::after of an element, as a rule for the copy (or null when it draws nothing). */
function pseudoRule(element, which, id, style) {
  const pseudo = getComputedStyle(element, which);
  const content = pseudo.getPropertyValue("content");
  if (!content || content === "none" || content === "normal") return null;
  const declarations = [`content:${content}`];
  const add = (name, value, unset) => {
    if (value && value !== unset) declarations.push(`${name}:${value}`);
  };
  for (const name of INHERITED) add(name, pseudo.getPropertyValue(name), style.getPropertyValue(name));
  for (const [name, unset] of Object.entries(OWN)) add(name, pseudo.getPropertyValue(name), unset);
  return `[data-cc="${id}"]${which}{${declarations.join(";")}}`;
}

function copyAttributes(copy) {
  copy.removeAttribute("style");
  for (const attribute of [...copy.attributes]) {
    const name = attribute.name;
    if (/^on/i.test(name) || !/^[A-Za-z_][-\w:.]*$/.test(name) || name === "srcset" || name === "loading" || name === "autofocus") copy.removeAttribute(name);
  }
}

function intersects(rect, view) {
  return rect.right > 0 && rect.bottom > 0 && rect.left < view.width && rect.top < view.height;
}

/**
 * The size and place of an element's box as the browser laid it out, even when a transform is
 * moving it (a hover effect): the transform is switched off for the moment it takes to measure.
 */
function layoutRect(element, style) {
  if (style.transform === "none") return element.getBoundingClientRect();
  const keep = ["transform", "transition"].map((name) => [name, element.style.getPropertyValue(name), element.style.getPropertyPriority(name)]);
  element.style.setProperty("transition", "none", "important");
  element.style.setProperty("transform", "none", "important");
  const rect = element.getBoundingClientRect();
  for (const [name, value, priority] of keep) {
    if (value) element.style.setProperty(name, value, priority);
    else element.style.removeProperty(name);
  }
  return rect;
}

const px = (value) => Number.parseFloat(value) || 0;

/**
 * Copies an element and what is inside it. `frame` says where the nearest positioned copy above
 * starts on screen ({ x, y }, the corner of its padding box) and whether the copy is inside a
 * transformed element (flow: true), where screen positions no longer match the layout.
 *
 * Block-level boxes are placed at the exact place they have on screen, whatever the browser's layout
 * did to get them there, so the picture and the page cannot drift apart; the text inside a box, and
 * things set in a line of text (inline), still flow, which only repeats what the browser did inside
 * a box of the same size.
 */
function copyNode(node, ctx, inSvg, frame) {
  if (node.nodeType === Node.TEXT_NODE) return document.createTextNode(xmlSafe(node.nodeValue ?? ""));
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const element = node;
  const svgNode = isSvgNode(element);
  const tag = element.localName.toLowerCase();
  if (SKIPPED_TAGS.has(tag) || ctx.exclude(element)) return null;

  const style = getComputedStyle(element);
  if (style.display === "none") {
    if (!svgNode) return null;
    if (!SVG_DEFINITIONS.has(tag)) return inSvg ? svgHidden(element) : null;
  }
  const insideDrawing = svgNode && inSvg;

  // A canvas (the 3D scene, a chart) becomes a picture of what it shows now.
  let copy;
  if (tag === "canvas") {
    copy = document.createElement("img");
    try {
      copy.setAttribute("src", element.toDataURL("image/png"));
    } catch {
      // A canvas that cannot be read stays blank.
    }
  } else {
    copy = element.cloneNode(false);
    copyAttributes(copy);
  }
  copyStyle(copy, style, insideDrawing);
  ctx.pairs?.push([element, copy]);

  let childFrame = frame;
  let hoist = false;
  if (!insideDrawing) {
    const display = style.display;
    const transformed = style.transform !== "none";
    const rect = display === "contents" ? null : layoutRect(element, style);
    const fixed = style.position === "fixed" && !frame.flow;
    const inline = display.startsWith("inline");

    if (rect && (fixed || (!frame.flow && !inline))) {
      // A box: put it where it is on screen.
      if (!fixed && rect.width * rect.height > 0 && !intersects(element.getBoundingClientRect(), ctx.view)) return null;
      const origin = fixed ? { x: 0, y: 0 } : frame;
      for (const [name, value] of [
        ["position", "absolute"], ["left", `${rect.left - origin.x}px`], ["top", `${rect.top - origin.y}px`], ["right", "auto"], ["bottom", "auto"],
        ["width", `${rect.width}px`], ["height", `${rect.height}px`], ["box-sizing", "border-box"],
        ["min-width", "0px"], ["min-height", "0px"], ["max-width", "none"], ["max-height", "none"],
        ["margin-top", "0px"], ["margin-right", "0px"], ["margin-bottom", "0px"], ["margin-left", "0px"], ["float", "none"], ["clear", "none"],
        ["grid-column-start", "auto"], ["grid-column-end", "auto"], ["grid-row-start", "auto"], ["grid-row-end", "auto"], ["align-self", "auto"], ["justify-self", "auto"],
      ]) {
        setProp(copy, name, value);
      }
      for (const axis of ["overflow-x", "overflow-y"]) if (/auto|scroll/.test(style.getPropertyValue(axis))) setProp(copy, axis, "hidden");
      childFrame = { x: rect.left + px(style.borderLeftWidth), y: rect.top + px(style.borderTopWidth), flow: transformed };
      hoist = fixed;
    } else if (rect && inline && display !== "inline") {
      // A thing set in a line of text (a button, an icon, an inline-block): it stays in the line, and
      // anything absolutely placed inside it is measured from its corner.
      if (style.position === "static") setProp(copy, "position", "relative");
      if (!frame.flow) childFrame = { x: rect.left + px(style.borderLeftWidth), y: rect.top + px(style.borderTopWidth), flow: transformed };
      else childFrame = { ...frame, flow: true };
    } else if (rect && display === "inline") {
      setProp(copy, "position", "static");
      if (transformed) childFrame = { ...frame, flow: true };
    } else if (transformed) {
      childFrame = { ...frame, flow: true };
    }
    if (frame.flow) childFrame = { ...childFrame, flow: true };
  }

  if (!insideDrawing && !LEAF_TAGS.has(tag) && style.whiteSpace === "normal" && element.firstChild && onOneLine(element)) setProp(copy, "white-space", "nowrap");
  if (tag === "input" || tag === "textarea") formValue(element, copy, tag);
  if (tag === "option" && element.selected) copy.setAttribute("selected", "selected");
  if (!svgNode) addPseudo(element, copy, style, ctx);
  if (tag === "img") ctx.tasks.push(inlineImage(element, copy));
  if (!insideDrawing) addBackground(style, copy, ctx);

  if (!LEAF_TAGS.has(tag)) {
    // Bare text in a flex or grid row is an item of its own, placed by the row's layout; with its
    // sibling boxes now placed by hand it would slide to the start of the row, so it is placed too.
    const textItems = !childFrame.flow && !svgNode && /flex|grid/.test(style.display);
    for (const child of element.childNodes) {
      if (textItems && child.nodeType === Node.TEXT_NODE && child.nodeValue.trim()) {
        copy.append(textItem(child, style, childFrame));
        continue;
      }
      if (tag === "svg" && child.nodeType === Node.ELEMENT_NODE && child.localName === "use") {
        inlineUse(child, copy);
        continue;
      }
      const childCopy = copyNode(child, ctx, svgNode, childFrame);
      if (childCopy) copy.append(childCopy);
    }
  }
  if (hoist) {
    // Fixed things (the top bar, the tab bar) sit directly on the picture, above the page.
    ctx.hoisted.push(copy);
    return null;
  }
  return copy;
}

/**
 * Whether all the text of an element (and its inline children) sits on one line. A box that is exactly
 * as wide as its one line of text would wrap in the picture, where letters are placed a hair
 * differently; such text is kept on its line.
 */
function onOneLine(element) {
  for (const child of element.children) {
    if (!getComputedStyle(child).display.startsWith("inline")) return false;
  }
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  const range = document.createRange();
  let lowestTop = -Infinity;
  let highestBottom = Infinity;
  let seen = false;
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (!node.nodeValue.trim()) continue;
    range.selectNodeContents(node);
    for (const rect of range.getClientRects()) {
      if (rect.width === 0) continue;
      seen = true;
      lowestTop = Math.max(lowestTop, rect.top);
      highestBottom = Math.min(highestBottom, rect.bottom);
    }
  }
  return seen && lowestTop < highestBottom;
}

/** A bare text node of a flex or grid row, as a small box placed where its text is on screen. */
function textItem(node, parentStyle, frame) {
  const range = document.createRange();
  range.selectNodeContents(node);
  const lines = range.getClientRects();
  const first = lines[0] ?? range.getBoundingClientRect();
  const whole = range.getBoundingClientRect();
  const lineHeight = px(parentStyle.lineHeight);
  const item = document.createElement("span");
  item.textContent = xmlSafe(node.nodeValue);
  setProp(item, "position", "absolute");
  setProp(item, "left", `${whole.left - frame.x}px`);
  setProp(item, "width", `${Math.ceil(whole.width) + 2}px`);
  if (lines.length <= 1) setProp(item, "white-space", "nowrap");
  if (lineHeight > 0) {
    // The text sits in the middle of a line box taller than the glyphs: start the box where that line starts.
    setProp(item, "top", `${first.top - (lineHeight - first.height) / 2 - frame.y}px`);
  } else {
    setProp(item, "top", `${first.top - frame.y}px`);
    setProp(item, "line-height", "normal");
  }
  return item;
}

function svgHidden(element) {
  const copy = element.cloneNode(false);
  copy.setAttribute("display", "none");
  return copy;
}

function formValue(element, copy, tag) {
  const type = (element.getAttribute("type") || "text").toLowerCase();
  if (tag === "textarea") {
    copy.textContent = xmlSafe(element.value);
    return;
  }
  // A password is never drawn, whatever the page shows for it.
  if (type === "password" || type === "file" || type === "hidden") {
    copy.removeAttribute("value");
    return;
  }
  if (type === "checkbox" || type === "radio") {
    if (element.checked) copy.setAttribute("checked", "checked");
    else copy.removeAttribute("checked");
    return;
  }
  copy.setAttribute("value", xmlSafe(element.value));
}

function addPseudo(element, copy, style, ctx) {
  const rules = [];
  const id = ctx.nextId + 1;
  for (const which of ["::before", "::after"]) {
    const rule = pseudoRule(element, which, id, style);
    if (rule) rules.push(rule);
  }
  if (!rules.length) return;
  ctx.nextId = id;
  copy.setAttribute("data-cc", String(id));
  ctx.rules.push(...rules);
}

function inlineImage(element, copy) {
  return async () => {
    const source = element.currentSrc || element.src;
    if (!source || source.startsWith("data:")) return;
    try {
      copy.setAttribute("src", await dataUrlOf(source));
    } catch {
      copy.removeAttribute("src"); // from another site: left blank
    }
  };
}

function addBackground(style, copy, ctx) {
  const image = style.getPropertyValue("background-image");
  if (!image || image === "none" || !image.includes("url(")) return;
  ctx.tasks.push(async () => {
    let value = image;
    for (const match of image.matchAll(/url\(["']?([^"')]+)["']?\)/g)) {
      if (match[1].startsWith("data:")) continue;
      try {
        value = value.replace(match[0], `url("${await dataUrlOf(match[1])}")`);
      } catch {
        value = value.replace(match[0], "none");
      }
    }
    setProp(copy, "background-image", value);
  });
}

/** A <use> element points at a sprite file the picture cannot load; the drawing itself is put in instead. */
function inlineUse(use, svgCopy) {
  const [file, id] = hrefOf(use).split("#");
  const symbol = id ? (file ? sprites.get(file)?.get(id) : document.getElementById(id)) : null;
  if (!symbol) return;
  if (!svgCopy.getAttribute("viewBox") && symbol.getAttribute("viewBox")) svgCopy.setAttribute("viewBox", symbol.getAttribute("viewBox"));
  const group = document.createElementNS(SVG_NS, "g");
  const drawing = symbol.localName === "symbol" ? [...symbol.childNodes] : [symbol];
  for (const child of drawing) group.append(document.importNode(child, true));
  svgCopy.append(group);
}

// ----------------------------------------------------------------------------- the picture

function pageBackground() {
  for (const element of [document.body, document.documentElement]) {
    const color = getComputedStyle(element).backgroundColor;
    if (color && color !== "rgba(0, 0, 0, 0)" && color !== "transparent") return color;
  }
  return "#ffffff";
}

function toBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

/**
 * A picture (PNG, or JPEG when the PNG would be too large) of an area of the visible page.
 * rect is { x, y, width, height } in CSS pixels from the top left of the visible page; exclude(element)
 * names elements to leave out (the assistant's own panel and the robot).
 */
export async function snapshotPage({ rect, exclude = () => false }) {
  const { wrapper, view } = await buildPage(exclude);
  return pictureOf(wrapper, view, rect);
}

/** The copy of the visible page, as a detached element (also used by the tests in the browser). */
export async function buildPage(exclude = () => false, pairs = null) {
  const view = { width: document.documentElement.clientWidth, height: document.documentElement.clientHeight };
  const ctx = { exclude, view, hoisted: [], tasks: [], rules: [], nextId: 0, pairs };
  const files = new Set([...document.querySelectorAll("use")].map((use) => hrefOf(use).split("#")[0]).filter(Boolean));
  await Promise.all([...files].map(loadSprite));
  const fonts = await loadFontCss();

  const root = document.documentElement;
  const rootStyle = getComputedStyle(root);
  const wrapper = document.createElement("div");
  copyStyle(wrapper, rootStyle, false);
  for (const [name, value] of [
    ["position", "relative"], ["display", "block"], ["overflow", "hidden"], ["margin", "0"], ["padding", "0"],
    ["width", `${view.width}px`], ["height", `${view.height}px`], ["background-color", pageBackground()],
  ]) {
    setProp(wrapper, name, value);
  }

  const body = copyNode(document.body, ctx, false, { x: 0, y: 0, flow: false });
  if (!body) throw new Error("nothing to draw");
  wrapper.append(body, ...ctx.hoisted);
  await Promise.all(ctx.tasks.map((task) => task()));

  const sheet = document.createElement("style");
  sheet.textContent = xmlSafe(`${fonts}\n${ctx.rules.join("\n")}`);
  wrapper.prepend(sheet);
  return { wrapper, view };
}

async function pictureOf(wrapper, view, rect) {
  const markup = new XMLSerializer().serializeToString(wrapper);
  const svg =
    `<svg xmlns="${SVG_NS}" width="${view.width}" height="${view.height}" viewBox="0 0 ${view.width} ${view.height}">` +
    `<foreignObject x="0" y="0" width="${view.width}" height="${view.height}">${markup}</foreignObject></svg>`;
  // A data: address, not a blob: one: the browser treats a blob: picture that contains HTML as
  // another origin's and refuses to let the page read it back.
  const image = new Image();
  image.src = `data:image/svg+xml;base64,${toBase64(svg)}`;
  await image.decode();

  const scale = outputScale(rect, window.devicePixelRatio);
  const { width, height } = outputSize(rect, scale);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  const draw = () => {
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(image, -rect.x * scale, -rect.y * scale, view.width * scale, view.height * scale);
  };
  draw();
  // Fonts inside a picture can arrive a moment after the first paint: draw once more.
  await new Promise((resolve) => setTimeout(resolve, 80));
  draw();

  let blob = await toBlob(canvas, "image/png");
  if (blob && blob.size > MAX_BYTES - 262144) blob = await toBlob(canvas, "image/jpeg", 0.9);
  if (!blob) throw new Error("empty picture");
  return blob;
}
