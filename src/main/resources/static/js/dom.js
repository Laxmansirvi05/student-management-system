// Small helpers for building page content from data. Text always goes in as text nodes,
// never as HTML, so nothing from the server can run as markup.

const SVG = "http://www.w3.org/2000/svg";

/**
 * Builds one element. The first argument is a tag with optional classes, for example
 * "div.card.reveal". Then an optional object of attributes, then the children.
 *
 *   h("a.more", { href: "/student/attendance.html" }, "View all", icon("chevron-right"))
 *
 * Attributes: "on..." keys add event listeners, "style" takes an object of CSS custom
 * properties, true writes an empty attribute, and null or false writes nothing.
 * Children: text, numbers, elements, or lists of them. Null and false are skipped.
 */
export function h(spec, ...rest) {
  const [tag, ...classes] = spec.split(".");
  const element = document.createElement(tag || "div");
  if (classes.length) {
    element.className = classes.join(" ");
  }
  const first = rest[0];
  const hasAttributes =
    first !== null && typeof first === "object" && !(first instanceof Node) && !Array.isArray(first);
  if (hasAttributes) {
    setAttributes(element, rest.shift());
  }
  append(element, rest);
  return element;
}

function setAttributes(element, attributes) {
  for (const [name, value] of Object.entries(attributes)) {
    if (value === null || value === undefined || value === false) {
      continue;
    }
    if (name.startsWith("on")) {
      element.addEventListener(name.slice(2), value);
    } else if (name === "style") {
      for (const [property, propertyValue] of Object.entries(value)) {
        element.style.setProperty(property, String(propertyValue));
      }
    } else {
      element.setAttribute(name, value === true ? "" : String(value));
    }
  }
}

function append(parent, children) {
  for (const child of children) {
    if (child === null || child === undefined || child === false) {
      continue;
    }
    if (Array.isArray(child)) {
      append(parent, child);
    } else if (child instanceof Node) {
      parent.append(child);
    } else {
      parent.append(document.createTextNode(String(child)));
    }
  }
}

/** A line icon from the project's icon file. It is decoration, so screen readers skip it. */
export function icon(name, extraClass = "") {
  const svg = document.createElementNS(SVG, "svg");
  svg.setAttribute("class", ("icon " + extraClass).trim());
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("focusable", "false");
  const use = document.createElementNS(SVG, "use");
  use.setAttribute("href", "/img/icons.svg#" + name);
  svg.append(use);
  return svg;
}

/** Replaces everything inside an element with the given children. */
export function fill(element, ...children) {
  element.replaceChildren();
  append(element, children);
  return element;
}

/** A status or category chip: tag("On track", "success", "check-circle"). */
export function tag(text, kind = "", iconName = "") {
  const classes = "span.tag" + (kind ? ".tag--" + kind : "") + (iconName ? "" : ".tag--dot");
  return h(classes, iconName ? icon(iconName) : null, text);
}

/** Gives each element its place in the entrance sequence, so a list arrives one by one. */
export function stagger(elements, start = 0) {
  elements.forEach((element, index) => element.style.setProperty("--i", String(start + index)));
  return elements;
}

/** "82.50%": two decimals, as the server sends percentages. */
export function percent(value) {
  return Number(value).toFixed(2) + "%";
}

const DAY_MONTH_YEAR = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});
const WEEKDAY_DATE = new Intl.DateTimeFormat("en-GB", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
});

/** "14 Oct 2026" from an ISO date ("2026-10-14") or an ISO timestamp ("2026-10-14T05:30:00Z"). */
export function formatDate(isoDate) {
  return DAY_MONTH_YEAR.format(new Date(isoDate.includes("T") ? isoDate : isoDate + "T00:00:00"));
}

/** "Tuesday, 14 October 2026" for today. */
export function formatToday() {
  return WEEKDAY_DATE.format(new Date());
}

/** Whole days from today to an ISO date. Negative when the date has passed. */
export function daysUntil(isoDate) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((new Date(isoDate + "T00:00:00") - today) / 86400000);
}

/** "2.4 MB" from a size in bytes. */
export function formatSize(bytes) {
  const megabytes = bytes / (1024 * 1024);
  return megabytes >= 1 ? megabytes.toFixed(1) + " MB" : Math.round(bytes / 1024) + " KB";
}

/** A short message at the bottom of the screen that leaves by itself. */
export function toast(message) {
  let box = document.getElementById("toast");
  if (!box) {
    box = h("div.toast", { id: "toast", role: "status" });
    document.body.append(box);
  }
  fill(box, icon("check-circle"), message);
  box.classList.add("is-in");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => box.classList.remove("is-in"), 2200);
}
