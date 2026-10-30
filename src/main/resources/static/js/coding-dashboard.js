import { formatDate, h, icon, tag } from "./dom.js";
import { accountFigures, count, journeyChart, platformMark, PLATFORMS, syncLine } from "./coding-parts.js";

export { platformMark };

// The coding portfolio dashboard (FR-COD-002, ADR 0017), shared by the student's own Portfolio page
// and the admin's read-only view of one student. A profile card on the left lists the linked sites;
// choosing one shows that site alone, choosing "All" shows the sites together, and GitHub opens the
// development view. Every number comes from the server; this module only draws it, as text and SVG.

const SVG = "http://www.w3.org/2000/svg";
const DAY = 86400000;
const SHORT_MONTH = new Intl.DateTimeFormat("en-GB", { month: "short" });

function svg(name, attributes = {}, ...children) {
  const element = document.createElementNS(SVG, name);
  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, String(value));
  }
  element.append(...children.filter(Boolean));
  return element;
}

function initialsOf(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
}

function card(title, ...children) {
  return h("section.card.cd-card", title ? h("h2.cd-card__title", title) : null, ...children);
}

function bigNumber(label, value, hint) {
  return h("div.cd-tile", h("p.cd-tile__value.num", count(value)), h("p.cd-tile__label", label), hint ? h("p.cd-tile__hint", hint) : null);
}

// ------------------------------------------------------------------------------------ Heatmap

function isoOf(day) {
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
}

/**
 * A calendar of weeks, one column a week from Sunday, that grows to the card's width. A day names
 * its count when it is pointed at, clicked or reached with the keyboard.
 */
function heatmap(calendar, { noun, months }) {
  const today = new Date();
  today.setHours(12, 0, 0, 0);
  const start = new Date(today.getFullYear(), today.getMonth() - (months - 1), 1, 12);
  start.setDate(start.getDate() - start.getDay());
  const values = Object.values(calendar).filter((value) => value > 0);
  const max = values.length ? Math.max(...values) : 0;
  const level = (value) => (!value ? 0 : max <= 4 ? Math.min(4, value) : Math.max(1, Math.min(4, Math.ceil((value / max) * 4))));
  const singular = noun.replace(/s$/, "");
  const caption = h("p.cd-heat__caption", { "aria-live": "polite" }, `Point at a day to see its ${noun}.`);
  const weeks = Math.ceil((today - start) / DAY / 7 + 0.0001);
  const grid = h("div.cd-heat__grid", { style: { "--weeks": String(weeks) } });
  const labels = h("div.cd-heat__months", { style: { "--weeks": String(weeks) } });
  for (let week = 0; week < weeks; week++) {
    const sunday = new Date(start.getTime() + week * 7 * DAY);
    // A column is named by the month whose first day it holds.
    for (let weekday = 0; weekday < 7; weekday++) {
      const day = new Date(sunday.getTime() + weekday * DAY);
      if (day.getDate() === 1 && day <= today) {
        labels.append(h("span.cd-heat__month", { style: { "grid-column": `${week + 1} / span 4` } }, SHORT_MONTH.format(day)));
      }
    }
    for (let weekday = 0; weekday < 7; weekday++) {
      const day = new Date(sunday.getTime() + weekday * DAY);
      day.setHours(12, 0, 0, 0);
      if (day > today) {
        grid.append(h("span.cd-heat__cell.cd-heat__cell--empty", { style: { "grid-row": String(weekday + 1), "grid-column": String(week + 1) } }));
        continue;
      }
      const iso = isoOf(day);
      const value = calendar[iso] ?? 0;
      const text = value ? `${value} ${value === 1 ? singular : noun} on ${formatDate(iso)}` : `No ${noun} on ${formatDate(iso)}`;
      const cell = h("button.cd-heat__cell.cd-heat__cell--" + level(value), { type: "button", title: text, "aria-label": text, style: { "grid-row": String(weekday + 1), "grid-column": String(week + 1) } });
      const show = () => (caption.textContent = text);
      cell.addEventListener("mouseenter", show);
      cell.addEventListener("focus", show);
      cell.addEventListener("click", show);
      grid.append(cell);
    }
  }
  const weekdays = h("div.cd-heat__days", ["", "Mon", "", "Wed", "", "Fri", ""].map((name) => h("span", name)));
  return h(
    "div.cd-heat",
    h("div.cd-heat__body", h("span"), labels, weekdays, grid),
    h("div.cd-heat__foot", caption, h("div.cd-heat__legend", h("span", "Less"), [0, 1, 2, 3, 4].map((n) => h("span.cd-heat__cell.cd-heat__cell--" + n)), h("span", "More"))),
  );
}

/** The activity tiles and the full-width calendar of one view. */
function activityBlock(calendar, activity, { noun, tiles }) {
  let months = 12;
  const body = h("div");
  const draw = () => body.replaceChildren(heatmap(calendar, { noun, months }));
  const range = h("select.select.cd-range", { "aria-label": "Period" }, h("option", { value: "12" }, "Last 12 months"), h("option", { value: "6" }, "Last 6 months"));
  range.addEventListener("change", () => {
    months = Number(range.value);
    draw();
  });
  draw();
  return [
    h("div.cd-tiles", tiles),
    h(
      "section.card.cd-card.cd-activity",
      h(
        "div.cd-activity__head",
        h("h2.cd-card__title", noun === "contributions" ? "Contribution activity" : "Submission activity"),
        h("span.cd-chip", "Total ", h("strong.num", count(activity.total))),
        h("span.cd-chip", "Max streak ", h("strong.num", count(activity.maxStreak))),
        h("span.cd-chip", "Current streak ", h("strong.num", count(activity.currentStreak))),
        h("span.cd-spacer"),
        range,
      ),
      body,
    ),
  ];
}

// ------------------------------------------------------------------------------------ Awards

/** HackerRank's levels: one or two stars bronze, three or four silver, five and up gold. */
function hackerRankLevel(stars) {
  return stars >= 5 ? "gold" : stars >= 3 ? "silver" : "bronze";
}

/** The icon of a skill badge (img/skills.svg), or null when it has none. */
function skillIcon(name) {
  const key = name.toLowerCase().trim();
  const icons = { "problem solving": "problem-solving", java: "java", python: "python", sql: "database", c: "c", "c language": "c", cpp: "cpp", "c++": "cpp", ruby: "ruby", react: "react", javascript: "javascript", js: "javascript" };
  return icons[key] ?? null;
}

/** What the badge says: "C" reads "C language", as on HackerRank. */
function skillLabel(name) {
  return name.trim().toLowerCase() === "c" ? "C language" : name;
}

/** A pointy-top hexagon with rounded corners, as an SVG path. */
function roundedHexagon(cx, cy, radius, corner) {
  const points = Array.from({ length: 6 }, (_, i) => {
    const angle = (Math.PI / 3) * i - Math.PI / 2;
    return [cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)];
  });
  let path = "";
  points.forEach((point, i) => {
    const before = points[(i + 5) % 6];
    const after = points[(i + 1) % 6];
    const toward = (from, to) => {
      const dx = to[0] - from[0];
      const dy = to[1] - from[1];
      const length = Math.hypot(dx, dy);
      return [from[0] + (dx / length) * corner, from[1] + (dy / length) * corner];
    };
    const start = toward(point, before);
    const end = toward(point, after);
    path += `${i === 0 ? "M" : "L"}${start[0].toFixed(1)},${start[1].toFixed(1)} Q${point[0].toFixed(1)},${point[1].toFixed(1)} ${end[0].toFixed(1)},${end[1].toFixed(1)} `;
  });
  return path + "Z";
}

/**
 * A HackerRank skill badge, drawn here because HackerRank publishes no badge pictures: a hexagon in
 * the level's metal, the skill's icon (or the day count of a "Days of" badge), its name and stars.
 */
function hackerRankBadge(award) {
  const level = hackerRankLevel(award.stars ?? 1);
  const id = "hr-" + Math.random().toString(36).slice(2, 9);
  const stars = Math.min(award.stars ?? 0, 6);
  const label = skillLabel(award.name);
  const days = /^(\d+)\s+days?\s+of\s+(.+)$/i.exec(award.name.trim());
  const icon = skillIcon(award.name);
  const mark = days
    ? svg("text", { x: 60, y: 62, class: "cd-hr__number", "text-anchor": "middle" }, days[1])
    : icon
      ? svg("use", { href: "/img/skills.svg#" + icon, x: 42, y: 26, width: 36, height: 36, class: "cd-hr__icon" })
      : svg("text", { x: 60, y: 60, class: "cd-hr__number", "text-anchor": "middle" }, label.charAt(0).toUpperCase());
  const words = days ? `Days of ${days[2]}` : label;
  const lines = words.length > 15 && words.includes(" ") ? [words.slice(0, words.lastIndexOf(" ", 15) > 0 ? words.lastIndexOf(" ", 15) : 15), words.slice(words.lastIndexOf(" ", 15) > 0 ? words.lastIndexOf(" ", 15) + 1 : 15)] : [words];
  return svg(
    "svg",
    { class: "cd-hr cd-hr--" + level, viewBox: "0 0 120 132", "aria-hidden": "true" },
    svg("defs", {}, svg("linearGradient", { id, x1: "0", y1: "0", x2: "0", y2: "1" }, svg("stop", { offset: "0", class: "cd-hr__light" }), svg("stop", { offset: "1", class: "cd-hr__dark" }))),
    svg("path", { d: roundedHexagon(60, 66, 62, 12), fill: `url(#${id})`, class: "cd-hr__shape" }),
    mark,
    ...lines.map((line, index) => svg("text", { x: 60, y: (lines.length === 1 ? 82 : 78) + index * 13, class: "cd-hr__name", "text-anchor": "middle" }, line)),
    svg("text", { x: 60, y: lines.length === 1 ? 101 : 106, class: "cd-hr__stars", "text-anchor": "middle" }, "★".repeat(stars)),
  );
}

/** A badge: the site's own picture when a copy is kept, HackerRank's design, or a plain medal. */
function badgeArt(award) {
  if (award.image) {
    return h("img.cd-award__img", { src: "/api/v1/coding-badges/" + award.image, alt: "", loading: "lazy", decoding: "async" });
  }
  if (award.platform === "HACKERRANK") {
    return hackerRankBadge(award);
  }
  return h("span.cd-award__medal", platformMark(award.platform, "large"));
}

function awardsCard(awards) {
  const shown = 10;
  const list = h("ul.cd-awards");
  const draw = (all) =>
    list.replaceChildren(
      ...(all ? awards : awards.slice(0, shown)).map((award) =>
        h(
          "li.cd-award",
          { title: `${award.name} · ${PLATFORMS[award.platform]}`, tabindex: "0" },
          h("span.cd-award__art", badgeArt(award)),
          h("span.cd-award__name", award.name),
          h("span.cd-award__site", PLATFORMS[award.platform]),
        ),
      ),
    );
  draw(false);
  let open = false;
  const more = awards.length > shown ? h("button.button.button--quiet.button--small", { type: "button" }, `Show all ${awards.length}`) : null;
  more?.addEventListener("click", () => {
    open = !open;
    draw(open);
    more.textContent = open ? "Show fewer" : `Show all ${awards.length}`;
  });
  return card(
    h("span.line.line--start", "Awards", h("span.cd-count.num", String(awards.length))),
    awards.length ? list : h("p.row__meta", "Badges from LeetCode, CodeChef and HackerRank appear here."),
    more,
  );
}

// ------------------------------------------------------------------------------------ Topics

function topicsCard(topics) {
  const shown = 10;
  const max = topics.length ? topics[0].count : 1;
  const list = h("ul.cd-topics");
  const draw = (all) =>
    list.replaceChildren(
      ...(all ? topics : topics.slice(0, shown)).map((topic) =>
        h(
          "li.cd-topic",
          { title: `${topic.count} problems on ${topic.name}` },
          h("span.cd-topic__name", topic.name),
          h("span.cd-topic__track", h("span.cd-topic__bar", { style: { "--share": `${Math.max(4, (topic.count / max) * 100)}%` } }, h("span.num", String(topic.count)))),
        ),
      ),
    );
  draw(false);
  let open = false;
  const more = topics.length > shown ? h("button.button.button--quiet.button--small", { type: "button" }, icon("chevron-down"), `Show all ${topics.length} topics`) : null;
  more?.addEventListener("click", () => {
    open = !open;
    draw(open);
    more.lastChild.textContent = open ? "Show the top 10" : `Show all ${topics.length} topics`;
  });
  return card("DSA topic analysis", topics.length ? list : h("p.row__meta", "Topics come from LeetCode and Codeforces."), more);
}

// ------------------------------------------------------------------------------------ Distribution

/** A ring split into coloured parts, the total in the middle, and a legend that highlights its part. */
function donut(parts, centre) {
  const total = parts.reduce((sum, part) => sum + part.value, 0);
  const radius = 42;
  const length = 2 * Math.PI * radius;
  const ring = svg("svg", { class: "cd-donut__ring", viewBox: "0 0 100 100", "aria-hidden": "true" }, svg("circle", { class: "cd-donut__track", cx: 50, cy: 50, r: radius }));
  let offset = 0;
  const arcs = parts.map((part) => {
    const share = total ? (part.value / total) * length : 0;
    const arc = svg("circle", { class: "cd-donut__part cd-tone--" + part.tone, cx: 50, cy: 50, r: radius, "stroke-dasharray": `${share} ${length - share}`, "stroke-dashoffset": String(-offset) });
    offset += share;
    ring.append(arc);
    return arc;
  });
  const legend = h(
    "ul.cd-legend",
    parts.map((part, index) => {
      const item = h("li.cd-legend__item", { tabindex: "0" }, h("span.cd-legend__name.cd-text--" + part.tone, part.label), h("strong.num", count(part.value)));
      const focus = (on) => arcs.forEach((arc, other) => arc.classList.toggle("is-dim", on && other !== index));
      item.addEventListener("mouseenter", () => focus(true));
      item.addEventListener("mouseleave", () => focus(false));
      item.addEventListener("focus", () => focus(true));
      item.addEventListener("blur", () => focus(false));
      return item;
    }),
  );
  return h("div.cd-donut", h("div.cd-donut__chart", ring, h("span.cd-donut__total.num", count(centre ?? total))), legend);
}

const TONES = { LeetCode: "orange", GeeksforGeeks: "success", HackerRank: "teal", Codeforces: "primary", CodeChef: "purple" };

function distributionCard(distribution) {
  const section = (title, sub, body) => h("div.cd-dist", h("h3.cd-dist__title", title), h("p.row__meta", sub), body);
  const sites = (list) => list.map((part) => ({ label: part.name === "GeeksforGeeks" ? "GfG Basic" : part.name, value: part.count, tone: TONES[part.name] ?? "primary" }));
  const sections = [];
  if (distribution.fundamentals.length) {
    sections.push(section("Fundamentals", "School and Basic problems, and HackerRank", donut(sites(distribution.fundamentals))));
  }
  const dsa = distribution.easy + distribution.medium + distribution.hard;
  if (dsa) {
    sections.push(
      section(
        "DSA",
        "By difficulty, LeetCode and GeeksforGeeks",
        donut([
          { label: "Easy", value: distribution.easy, tone: "success" },
          { label: "Medium", value: distribution.medium, tone: "warning" },
          { label: "Hard", value: distribution.hard, tone: "danger" },
        ]),
      ),
    );
  }
  if (distribution.competitive.length) {
    sections.push(section("Competitive programming", "Codeforces and CodeChef", donut(sites(distribution.competitive))));
  }
  return card("Question distribution", sections.length ? sections : h("p.row__meta", "Verify a site to see how your problems split."));
}

function contestsCard(contests) {
  if (!contests.length) {
    return null;
  }
  return card(
    "Contest ratings",
    h(
      "ul.cd-contests",
      contests.map((contest) =>
        h(
          "li.cd-contest",
          platformMark(contest.platform),
          h("div.stack.stack--tight", h("strong", PLATFORMS[contest.platform]), contest.rank ? h("span.row__meta", contest.rank) : null),
          h("div.cd-contest__numbers", h("strong.num", count(contest.rating)), h("span.row__meta", `best ${count(contest.maxRating)} · ${count(contest.contests)} contests`)),
        ),
      ),
    ),
  );
}

// ------------------------------------------------------------------------------------ Views

function problemSolvingView(portfolio) {
  const insights = portfolio.problemSolving;
  const journey = journeyChart(portfolio.journey);
  return h(
    "div.cd-view",
    activityBlock(insights.calendar, insights.activity, {
      noun: "submissions",
      tiles: [
        bigNumber("Questions solved", insights.questionsSolved),
        bigNumber("Active days", insights.activity.activeDays),
        bigNumber("Submissions", insights.activity.total, "Last 12 months"),
        bigNumber("Max streak", insights.activity.maxStreak, "days"),
        bigNumber("Current streak", insights.activity.currentStreak, "days"),
      ],
    }),
    h(
      "div.cd-split",
      h("div.cd-column", awardsCard(insights.awards), topicsCard(insights.topics), journey ? card("Your journey", h("div.coding-chart-wrap.cd-journey", journey)) : null),
      h("div.cd-column", distributionCard(insights.distribution), contestsCard(insights.contests)),
    ),
  );
}

const LANGUAGE_TONES = ["primary", "danger", "success", "warning", "purple", "teal", "orange"];

function developmentView(development, account, options) {
  const actions =
    account && options.accountActions
      ? h("section.card.cd-owner", h("span.line.line--start", platformMark("GITHUB"), h("a.row__link", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle)), syncLine(account), h("span.cd-spacer"), options.accountActions(account))
      : null;
  if (!development) {
    return h("div.cd-view", actions, card(null, h("div.empty", h("h3", "No GitHub numbers yet"), h("p", "Verify GitHub, or press Refresh, to see contributions, languages and projects."))));
  }
  const languages = Object.entries(development.languages);
  const stat = (iconName, label, value) => h("li.cd-stat", h("span.cd-stat__icon", icon(iconName)), h("span", label), h("strong.num", count(value)));
  return h(
    "div.cd-view",
    actions,
    activityBlock(development.calendar, development.contributions, {
      noun: "contributions",
      tiles: [
        bigNumber("Contributions", development.contributions.total, "Last 12 months"),
        bigNumber("Active days", development.contributions.activeDays),
        bigNumber("Max streak", development.contributions.maxStreak, "days"),
        bigNumber("Current streak", development.contributions.currentStreak, "days"),
        bigNumber("Repositories", development.publicRepos),
      ],
    }),
    h(
      "div.cd-split.cd-split--even",
      card(
        "Languages",
        languages.length
          ? h(
              "div.stack",
              h("div.cd-langbar", languages.map(([name, share], index) => h("span.cd-langbar__part.cd-tone-bg--" + LANGUAGE_TONES[index % LANGUAGE_TONES.length], { style: { "--share": share + "%" }, title: `${name} ${share}%` }))),
              h("ul.cd-langs", languages.map(([name, share], index) => h("li", h("span.cd-dot.cd-tone-bg--" + LANGUAGE_TONES[index % LANGUAGE_TONES.length]), h("span", name), h("span.row__meta.num", share + "%")))),
              h("p.row__meta", "Share of public repositories by main language."),
            )
          : h("p.row__meta", "No languages yet."),
      ),
      card(
        "Stats",
        h(
          "ul.cd-stats",
          stat("target", "Stars", development.stars),
          stat("check-circle", "Commits", development.commits),
          stat("arrow-up-right", "Pull requests", development.pullRequests),
          stat("alert", "Issues", development.issues),
          stat("users", "Followers", development.followers),
        ),
        development.commits === null ? h("p.row__meta", "GitHub's search did not answer on the last read; these fill in on the next one.") : null,
      ),
    ),
    card(
      h("span.line.line--start", "Projects", h("span.cd-count.num", String(development.projects.length))),
      development.projects.length
        ? h(
            "ul.cd-projects",
            development.projects.map((project) =>
              h(
                "li.cd-project",
                h("div.cd-project__head", h("span.cd-project__icon", platformMark("GITHUB")), h("strong", project.name), h("a.icon-button.icon-button--bare", { href: project.url, target: "_blank", rel: "noopener noreferrer", "aria-label": `Open ${project.name} on GitHub` }, icon("external"))),
                h("p.cd-project__text", project.description || "No description."),
                h("div.line.line--start.line--wrap", project.language ? tag(project.language) : null, h("span.row__meta", "★ " + count(project.stars ?? 0)), h("span.row__meta.cd-project__path", `${development.handle}/${project.name}`)),
              ),
            ),
          )
        : h("p.row__meta", "No public repositories yet."),
    ),
  );
}

/** One site alone: its numbers, its own activity, topics and badges, or how to verify it. */
function accountView(account, options) {
  if (account.status !== "VERIFIED") {
    return h("div.cd-view", options.pendingBody ? card(`Verify ${account.platformName}`, options.pendingBody(account)) : card(account.platformName, h("p.row__meta", "Waiting for the student to verify this account.")));
  }
  const details = account.details;
  const topics = Object.entries(details.topics)
    .sort((a, b) => b[1] - a[1])
    .map(([name, value]) => ({ name, count: value }));
  const awards = details.badges.map((badge) => ({ platform: account.platform, name: badge.name, stars: badge.stars, image: badge.image }));
  const hasCalendar = Object.keys(details.calendar).length > 0;
  return h(
    "div.cd-view",
    card(
      h("span.line.line--start", platformMark(account.platform, "large"), account.platformName, account.rankLabel ? tag(account.rankLabel, "purple") : null),
      h("a.row__link", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle),
      accountFigures(account),
      syncLine(account),
      options.accountActions ? options.accountActions(account) : null,
    ),
    hasCalendar
      ? activityBlock(details.calendar, account.activity, {
          noun: "submissions",
          tiles: [bigNumber("Solved", account.problemsSolved), bigNumber("Active days", account.activity.activeDays), bigNumber("Max streak", account.activity.maxStreak, "days"), bigNumber("Current streak", account.activity.currentStreak, "days")],
        })
      : null,
    awards.length ? awardsCard(awards) : null,
    topics.length ? topicsCard(topics) : null,
  );
}

// ------------------------------------------------------------------------------------ Profile card

/** The student's text, four lines at first, with a toggle when it is longer. */
function bio(text) {
  const body = h("p.cd-profile__bio.is-clamped", text);
  if (text.length < 180) {
    body.classList.remove("is-clamped");
    return body;
  }
  const toggle = h("button.cd-profile__more-text", { type: "button", "aria-expanded": "false" }, "Read more");
  toggle.addEventListener("click", () => {
    const open = body.classList.toggle("is-clamped") === false;
    toggle.setAttribute("aria-expanded", String(open));
    toggle.textContent = open ? "Show less" : "Read more";
  });
  return h("div.cd-profile__bio-wrap", body, toggle);
}

function profileCard(portfolio, state, options, select) {
  const student = portfolio.student;
  const avatar = h("span.cd-avatar", initialsOf(student.fullName));
  if (options.photoUrl && student.hasPhoto) {
    options
      .photoUrl()
      .then((url) => {
        if (url) {
          const image = h("img", { alt: "" });
          image.addEventListener("error", () => avatar.replaceChildren(initialsOf(student.fullName)), { once: true });
          image.src = url;
          avatar.replaceChildren(image);
        }
      })
      .catch(() => {});
  }
  const row = (key, label, mark, account) => {
    const selected = state.selected === key;
    const button = h(
      "button.cd-site" + (selected ? ".is-selected" : ""),
      { type: "button", "aria-pressed": String(selected) },
      mark,
      h("span.cd-site__name", label),
      account ? (account.status === "VERIFIED" ? h("span.cd-tick", { title: "Verified", "aria-label": "Verified" }, icon("check")) : tag("Pending", "warning")) : null,
    );
    button.addEventListener("click", () => select(key));
    return h("li.cd-site-row", button, account ? h("a.icon-button.icon-button--bare", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer", "aria-label": `Open ${label} profile` }, icon("external")) : null);
  };
  const solving = portfolio.accounts.filter((account) => account.platform !== "GITHUB");
  const github = portfolio.accounts.find((account) => account.platform === "GITHUB");
  return h(
    "aside.card.cd-profile",
    options.headerControls ? options.headerControls(portfolio) : null,
    h("div.cd-profile__who", avatar, h("h2.cd-profile__name", student.fullName), h("p.row__meta", `${student.rollNo} · ${student.branch} · Semester ${student.semester} · Section ${student.section}`), student.bio ? bio(student.bio) : null),
    h("div.cd-profile__totals", h("div", h("strong.num", count(portfolio.totalSolved)), h("span.row__meta", "solved")), h("div", h("strong.num", String(portfolio.verifiedAccounts)), h("span.row__meta", "verified sites"))),
    h("p.eyebrow", "Problem solving"),
    h("ul.cd-sites", row("ALL", "All platforms", h("span.cd-logo.cd-logo--all", { "aria-hidden": "true" }, icon("grid")), null), solving.map((account) => row(account.platform, account.platformName, platformMark(account.platform), account))),
    options.addPlatform ? options.addPlatform() : null,
    h("p.eyebrow", "Development"),
    h("ul.cd-sites", github ? row("GITHUB", "GitHub", platformMark("GITHUB"), github) : h("li.row__meta", "No GitHub account linked.")),
    options.footer ? options.footer(portfolio) : null,
  );
}

/**
 * Draws the dashboard into container. options: headerControls, addPlatform, footer, accountActions
 * and pendingBody return elements for the owner; photoUrl returns a promise of the photo link.
 */
export function renderDashboard(container, portfolio, options = {}, state = { selected: "ALL" }) {
  const select = (key) => {
    state.selected = key;
    renderDashboard(container, portfolio, options, state);
  };
  if (state.selected !== "ALL" && state.selected !== "GITHUB" && !portfolio.accounts.some((account) => account.platform === state.selected)) {
    state.selected = "ALL";
  }
  const github = portfolio.accounts.find((account) => account.platform === "GITHUB");
  let main;
  if (state.selected === "ALL") {
    main = problemSolvingView(portfolio);
  } else if (state.selected === "GITHUB") {
    main = github && github.status !== "VERIFIED" ? accountView(github, options) : developmentView(portfolio.development, github, options);
  } else {
    main = accountView(
      portfolio.accounts.find((account) => account.platform === state.selected),
      options,
    );
  }
  container.replaceChildren(h("div.cd-layout", profileCard(portfolio, state, options, select), h("div.cd-main", main)));
}
