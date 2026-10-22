// Putting stored note HTML into the page under this application's Content Security Policy.
//
// The policy allows no inline styles (style-src 'self'). A style attribute that arrives as markup,
// through innerHTML or setAttribute or even a parser, is ignored and reported as a violation. A
// note keeps its text alignment, colours, sizes and highlights in style attributes, so every place
// that shows stored HTML builds its nodes here: the style attributes are renamed before parsing,
// and each style is then set through the CSS object model, which the policy does not restrict.
// The server has already rebuilt the HTML from an allowlist, so what arrives is markup of known
// tags and values only.

const TAG = /<[a-zA-Z][^<>]*>/g;
const STYLE_ATTRIBUTE = /(\s)style="([^"]*)"/g;

const RENAMED = /(\s)data-nt-style="([^"]*)"/g;

/** HTML with every style attribute renamed to data-nt-style, so parsing it raises no violation. */
export function hideStyles(html) {
  return (html || "").replace(TAG, (tag) => tag.replace(STYLE_ATTRIBUTE, '$1data-nt-style="$2"'));
}

/** The reverse of hideStyles, for HTML that is about to be stored. */
export function showStyles(html) {
  return html.replace(TAG, (tag) => tag.replace(RENAMED, '$1style="$2"'));
}

/** The nodes of a piece of HTML, ready to be put into the page. */
export function htmlToFragment(html) {
  const renamed = hideStyles(html);
  const parsed = new DOMParser().parseFromString("<!doctype html><body>" + renamed, "text/html");
  const fragment = document.createDocumentFragment();
  for (const node of [...parsed.body.childNodes]) {
    fragment.append(document.importNode(node, true));
  }
  for (const element of fragment.querySelectorAll("[data-nt-style]")) {
    element.style.cssText = element.getAttribute("data-nt-style");
    element.removeAttribute("data-nt-style");
  }
  return fragment;
}

/** Replaces the content of an element with stored HTML. */
export function setHtml(element, html) {
  element.replaceChildren(htmlToFragment(html));
}
