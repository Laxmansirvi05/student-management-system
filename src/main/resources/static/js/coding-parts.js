import { formatDate, h } from "./dom.js";

// Pieces the coding portfolio and both leaderboards share (FR-COD-002 to FR-COD-005): the names
// of the sites, one account's numbers, and the journey chart. Everything from the server goes in as
// text; the chart is built from SVG elements, never from markup.

const SVG = "http://www.w3.org/2000/svg";

export const PLATFORMS = {
  GITHUB: "GitHub",
  LEETCODE: "LeetCode",
  CODEFORCES: "Codeforces",
  CODECHEF: "CodeChef",
  GEEKSFORGEEKS: "GeeksforGeeks",
  HACKERRANK: "HackerRank",
};

const NUMBER = new Intl.NumberFormat("en-IN");

/** "1,234", or a dash when the site does not have that number. */
export function count(value) {
  return value === null || value === undefined ? "—" : NUMBER.format(value);
}

const LOGOS = { GITHUB: "github", LEETCODE: "leetcode", CODEFORCES: "codeforces", CODECHEF: "codechef", GEEKSFORGEEKS: "geeksforgeeks", HACKERRANK: "hackerrank" };

/** A site's logo on a light tile, in the site's own colour (img/brands.svg). */
export function platformMark(platform, size = "") {
  const logo = svg("svg", { class: "cd-logo__svg", "aria-hidden": "true", focusable: "false" }, svg("use", { href: "/img/brands.svg#" + (LOGOS[platform] ?? "github") }));
  return h("span.cd-logo.cd-logo--" + platform.toLowerCase() + (size ? ".cd-logo--" + size : ""), { "aria-hidden": "true" }, logo);
}

/** The logos of the sites a student has verified, named for screen readers. */
export function platformTags(platforms) {
  return h(
    "span.line.line--start.line--wrap.coding-platforms",
    { "aria-label": platforms.map((platform) => PLATFORMS[platform] ?? platform).join(", ") },
    platforms.map((platform) => h("span", { title: PLATFORMS[platform] ?? platform }, platformMark(platform, "small"))),
  );
}

function figure(label, value) {
  return value === null || value === undefined ? null : h("div.coding-figure", h("span.coding-figure__value.num", count(value)), h("span.coding-figure__label", label));
}

/** One account's numbers, only those its site gives. */
export function accountFigures(account) {
  const figures = account.countsProblems
    ? [
        figure("Solved", account.problemsSolved),
        figure("Easy", account.easySolved),
        figure("Medium", account.mediumSolved),
        figure("Hard", account.hardSolved),
        figure("Rating", account.contestRating),
        figure("Best rating", account.maxRating),
        figure("Contests", account.contestsAttended),
        figure("Score", account.score),
      ]
    : [figure("Public repos", account.publicRepos), figure("Stars", account.stars), figure("Followers", account.followers)];
  const shown = figures.filter(Boolean);
  return shown.length ? h("div.coding-figures", shown) : h("p.row__meta", "No numbers yet.");
}

/** When the numbers were last read, and why the last try failed if it did. */
export function syncLine(account) {
  if (!account.lastSyncedAt) {
    return null;
  }
  const failed = account.lastSyncError === "NOT_FOUND" ? " · The profile could not be found last time." : account.lastSyncError === "UNAVAILABLE" ? " · The site did not answer last time." : "";
  return h("p.row__meta" + (failed ? ".row__meta--warning" : ""), "Updated " + formatDate(account.lastSyncedAt) + failed);
}

function svg(name, attributes = {}, ...children) {
  const element = document.createElementNS(SVG, name);
  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, String(value));
  }
  element.append(...children.filter(Boolean));
  return element;
}

/**
 * The journey: total problems solved over time, as a line. points is [{date, problemsSolved}] in
 * date order. One point is drawn as a dot; none gives nothing.
 */
export function journeyChart(points) {
  if (!points.length) {
    return null;
  }
  const width = 640;
  const height = 200;
  const pad = { left: 44, right: 16, top: 16, bottom: 28 };
  const values = points.map((point) => point.problemsSolved);
  const low = Math.min(...values);
  const high = Math.max(...values);
  const span = Math.max(1, high - low);
  const first = new Date(points[0].date).getTime();
  const last = new Date(points[points.length - 1].date).getTime();
  const days = Math.max(1, last - first);
  const x = (point) => pad.left + ((new Date(point.date).getTime() - first) / days) * (width - pad.left - pad.right);
  const y = (value) => height - pad.bottom - ((value - low) / span) * (height - pad.top - pad.bottom);
  const line = points.map((point) => `${x(point).toFixed(1)},${y(point.problemsSolved).toFixed(1)}`).join(" ");
  const end = points[points.length - 1];
  const label = (text, attributes) => svg("text", { class: "coding-chart__label", ...attributes }, text);
  const chart = svg(
    "svg",
    { class: "coding-chart", viewBox: `0 0 ${width} ${height}`, role: "img", "aria-label": `Problems solved went from ${count(points[0].problemsSolved)} on ${formatDate(points[0].date)} to ${count(end.problemsSolved)} on ${formatDate(end.date)}.` },
    svg("line", { class: "coding-chart__axis", x1: pad.left, y1: height - pad.bottom, x2: width - pad.right, y2: height - pad.bottom }),
    label(count(high), { x: pad.left - 8, y: pad.top + 4, "text-anchor": "end" }),
    label(count(low), { x: pad.left - 8, y: height - pad.bottom, "text-anchor": "end" }),
    label(formatDate(points[0].date), { x: pad.left, y: height - 8 }),
    points.length > 1 ? label(formatDate(end.date), { x: width - pad.right, y: height - 8, "text-anchor": "end" }) : null,
    points.length > 1 ? svg("polyline", { class: "coding-chart__line", points: line }) : null,
    svg("circle", { class: "coding-chart__dot", cx: x(end).toFixed(1), cy: y(end.problemsSolved).toFixed(1), r: 4.5 }),
  );
  return chart;
}
