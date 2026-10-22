import { h } from "../dom.js";

// The icons of the notes workspace, the robot and the quick assistant are Material Symbols (the
// icon font of the original design), served from this application as /fonts/material-symbols.woff2.
// A symbol is a text node in that font, so it is decoration: screen readers skip it.

/** A Material Symbols icon by name, for example symbol("format_bold"). */
export function symbol(name, extraClass = "") {
  return h("span.material-symbols-outlined" + (extraClass ? "." + extraClass : ""), { "aria-hidden": "true" }, name);
}
