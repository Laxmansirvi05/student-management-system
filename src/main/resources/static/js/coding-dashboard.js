import { formatDate, h, icon, tag } from "./dom.js";
import { accountFigures, count, journeyChart, PLATFORMS, syncLine } from "./coding-parts.js";

// The coding portfolio dashboard (FR-COD-002, ADR 0017), shared by the student's own Portfolio page
// and the admin's read-only view of one student. A profile card on the left lists the linked sites;
// choosing one shows that site alone, choosing "All" shows the sites together, and GitHub opens the
// development view. Every number comes from the server; this module only draws it, as text and SVG.

const SVG = "http://www.w3.org/2000/svg";
const DAY = 86400000;
const SHORT_MONTH = new Intl.DateTimeFormat("en-GB", { month: "short" });
const MARKS = { GITHUB: "GH", LEETCODE: "LC", CODEFORCES: "CF", CODECHEF: "CC", GEEKSFORGEEKS: "GfG", HACKERRANK: "HR" };

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

/** A site's round mark, in the site's own colour. */
export function platformMark(platform) {
  return h("span.cd-mark.cd-mark--" + platform.toLowerCase(), { "aria-hidden": "true" }, MARKS[platform] ?? "?");
}

function card(title, ...children) {
  return h("section.card.cd-card", title ? h("h2.cd-card__title", title) : null, ...children);
}

function bigNumber(label, value, hint) {
  return h("section.card.cd-card.cd-big", h("p.cd-big__label", label), h("p.cd-big__value.num", count(value)), hint ? h("p.row__meta", hint) : null);
}

// ------------------------------------------------------------------------------------ Heatmap

/** Day cells grouped by month, columns of weeks; a click or focus names the day below the grid. */
function heatmap(calendar, { noun, months }) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const first = new Date(today.getFullYear(), today.getMonth() - (months - 1), 1);
  const values = Object.values(calendar).filter((value) => value > 0);
  const max = values.length ? Math.max(...values) : 0;
  const level = (value) => (!value ? 0 : max <= 4 ? Math.min(4, value) : Math.min(4, Math.ceil((value / max) * 4)));
  const caption = h("p.cd-heat__caption.row__meta", { "aria-live": "polite" }, `Choose a day to see its ${noun}.`);
  const blocks = [];
  for (let m = 0; m < months; m++) {
    const start = new Date(first.getFullYear(), first.getMonth() + m, 1);
    const end = new Date(start.getFullYear(), start.getMonth() + 1, 0);
    const cells = [];
    for (let pad = 0; pad < start.getDay(); pad++) {
      cells.push(h("span.cd-heat__cell.cd-heat__cell--blank"));
    }
    for (let day = new Date(start); day <= end; day = new Date(day.getTime() + DAY + 3600000)) {
      day.setHours(0, 0, 0, 0);
      const iso = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
      const value = calendar[iso] ?? 0;
      const future = day > today;
      const text = `${value} ${value === 1 ? noun.replace(/s$/, "") : noun} on ${formatDate(iso)}`;
      const cell = h("button.cd-heat__cell.cd-heat__cell--" + (future ? "future" : level(value)), { type: "button", title: text, "aria-label": text, disabled: future });
      cell.addEventListener("click", () => (caption.textContent = text));
      cell.addEventListener("focus", () => (caption.textContent = text));
      cells.push(cell);
    }
    blocks.push(h("div.cd-heat__month", h("div.cd-heat__grid", cells), h("span.cd-heat__label", SHORT_MONTH.format(start))));
  }
  // Laid out right to left, so the scroll starts at the newest month and older ones are to the left.
  const scroller = h("div.cd-heat__scroll", blocks.reverse());
  return h("div.cd-heat", scroller, h("div.cd-heat__legend", h("span.row__meta", "Less"), [0, 1, 2, 3, 4].map((n) => h("span.cd-heat__cell.cd-heat__cell--" + n)), h("span.row__meta", "More")), caption);
}

function activityCard(calendar, activity, { noun, totalLabel }) {
  let months = 6;
  const body = h("div");
  const draw = () => body.replaceChildren(heatmap(calendar, { noun, months }));
  const range = h("select.select.cd-range", { "aria-label": "Period" }, h("option", { value: "6" }, "Last 6 months"), h("option", { value: "12" }, "Last 12 months"));
  range.addEventListener("change", () => {
    months = Number(range.value);
    draw();
  });
  draw();
  return h(
    "section.card.cd-card.cd-activity",
    h(
      "div.cd-activity__head",
      h("span.cd-chip", totalLabel, " ", h("strong.num", count(activity.total))),
      h("span.cd-chip", "Max streak ", h("strong.num", count(activity.maxStreak))),
      h("span.cd-chip", "Current streak ", h("strong.num", count(activity.currentStreak))),
      h("span.cd-spacer"),
      range,
    ),
    body,
  );
}

// ------------------------------------------------------------------------------------ Awards

function badgeKind(award) {
  const name = award.name.toLowerCase();
  if (/gold|guardian|knight/.test(name) || award.stars >= 5) return "gold";
  if (/silver/.test(name) || award.stars >= 3) return "silver";
  if (/bronze/.test(name) || award.stars >= 1) return "bronze";
  return "plain";
}

function awardsCard(awards) {
  const shown = 8;
  const list = h("ul.cd-awards");
  const draw = (all) =>
    list.replaceChildren(
      ...(all ? awards : awards.slice(0, shown)).map((award) =>
        h(
          "li.cd-award",
          { title: `${award.name} · ${PLATFORMS[award.platform]}` },
          h("span.cd-award__hex.cd-award__hex--" + badgeKind(award), h("span.cd-award__mark", MARKS[award.platform]), award.stars ? h("span.cd-award__stars", "★".repeat(Math.min(award.stars, 7))) : null),
          h("span.cd-award__name", award.name),
        ),
      ),
    );
  draw(false);
  let open = false;
  const more =
    awards.length > shown
      ? h("button.button.button--quiet.button--small", { type: "button" }, `Show all ${awards.length}`)
      : null;
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
    h(
      "div.cd-top",
      bigNumber("Questions solved", insights.questionsSolved),
      bigNumber("Active days", insights.activity.activeDays),
      activityCard(insights.calendar, insights.activity, { noun: "submissions", totalLabel: "Submissions" }),
    ),
    h("div.cd-split", h("div.cd-column", awardsCard(insights.awards), topicsCard(insights.topics), contestsCard(insights.contests)), h("div.cd-column", distributionCard(insights.distribution))),
    journey ? card("Your journey", h("div.coding-chart-wrap", journey)) : null,
  );
}

function developmentView(development, account, options) {
  const actions =
    account && options.accountActions
      ? h("section.card.cd-card.cd-owner", h("div.line.line--wrap", h("span.line.line--start", platformMark("GITHUB"), h("a.row__link", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle)), syncLine(account), options.accountActions(account)))
      : null;
  if (!development) {
    return card(null, h("div.empty", h("h3", "No GitHub account yet"), h("p", "Link and verify GitHub to see contributions, languages and projects.")));
  }
  const languages = Object.entries(development.languages);
  const LANGUAGE_TONES = ["danger", "success", "warning", "primary", "purple", "teal", "orange"];
  const stat = (iconName, label, value) => h("li.cd-stat", icon(iconName), h("span", label), h("strong.num", count(value)));
  return h(
    "div.cd-view",
    actions,
    h(
      "div.cd-top",
      bigNumber("Total contributions", development.contributions.total, "In the last year"),
      bigNumber("Total active days", development.contributions.activeDays),
      activityCard(development.calendar, development.contributions, { noun: "contributions", totalLabel: "Contributions" }),
    ),
    h(
      "div.cd-split.cd-split--even",
      card(
        "Languages",
        languages.length
          ? h(
              "div.stack",
              h("div.cd-langbar", languages.map(([name, share], index) => h("span.cd-langbar__part.cd-tone-bg--" + LANGUAGE_TONES[index % LANGUAGE_TONES.length], { style: { "--share": share + "%" }, title: `${name} ${share}%` }))),
              h("ul.cd-langs", languages.map(([name, share], index) => h("li", h("span.cd-dot.cd-tone-bg--" + LANGUAGE_TONES[index % LANGUAGE_TONES.length]), h("span", name), h("span.row__meta.num", share + "%")))),
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
          stat("book-open", "Public repositories", development.publicRepos),
          stat("users", "Followers", development.followers),
        ),
        development.commits === null ? h("p.row__meta", "GitHub's search did not answer on the last read; these fill in on the next one.") : null,
      ),
    ),
    card(
      "Projects",
      development.projects.length
        ? h(
            "ul.cd-projects",
            development.projects.map((project) =>
              h(
                "li.cd-project",
                h("div.cd-project__head", h("strong", project.name), h("a.icon-button.icon-button--bare", { href: project.url, target: "_blank", rel: "noopener noreferrer", "aria-label": `Open ${project.name} on GitHub` }, icon("external"))),
                project.description ? h("p.row__meta", project.description) : null,
                h("div.line.line--start.line--wrap", project.language ? tag(project.language) : null, h("span.row__meta", "★ " + count(project.stars ?? 0)), h("span.row__meta", `${development.handle}/${project.name}`)),
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
  const awards = details.badges.map((badge) => ({ platform: account.platform, name: badge.name, stars: badge.stars }));
  const hasCalendar = Object.keys(details.calendar).length > 0;
  return h(
    "div.cd-view",
    card(
      h("span.line.line--start", platformMark(account.platform), account.platformName, account.rankLabel ? tag(account.rankLabel, "purple") : null),
      h("a.row__link", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle),
      accountFigures(account),
      syncLine(account),
      options.accountActions ? options.accountActions(account) : null,
    ),
    hasCalendar ? activityCard(details.calendar, account.activity, { noun: account.platform === "GITHUB" ? "contributions" : "submissions", totalLabel: "Total" }) : null,
    awards.length ? awardsCard(awards) : null,
    topics.length ? topicsCard(topics) : null,
  );
}

// ------------------------------------------------------------------------------------ Profile card

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
    h("div.cd-profile__who", avatar, h("h2.cd-profile__name", student.fullName), h("p.row__meta", `${student.rollNo} · ${student.branch} · Semester ${student.semester} · Section ${student.section}`), student.bio ? h("p.cd-profile__bio", student.bio) : null),
    h("div.cd-profile__totals", h("div", h("strong.num", count(portfolio.totalSolved)), h("span.row__meta", "solved")), h("div", h("strong.num", String(portfolio.verifiedAccounts)), h("span.row__meta", "verified sites"))),
    h("p.eyebrow", "Problem solving"),
    h("ul.cd-sites", row("ALL", "All platforms", h("span.cd-mark.cd-mark--all", { "aria-hidden": "true" }, icon("grid")), null), solving.map((account) => row(account.platform, account.platformName, platformMark(account.platform), account))),
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
