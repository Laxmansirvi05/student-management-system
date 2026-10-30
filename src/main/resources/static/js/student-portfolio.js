import { confirmAction, field, input, openForm, select } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { fill, h, icon, tag, toast } from "./dom.js";
import { accountFigures, journeyChart, PLATFORMS, syncLine } from "./coding-parts.js";
import { countUp, reveal } from "./motion.js";
import { cardHead, empty, loadInto } from "./parts.js";
import "./shell.js";

// The coding portfolio (FR-COD-001 to FR-COD-003, ADR 0017). The student links an account, puts the
// code shown here in their profile on that site, and presses Verify; the server reads the public
// profile. Only verified accounts count. The journey is drawn from the server's daily snapshots.

const content = document.getElementById("content");
let portfolio = null;

function summary() {
  const total = h("p.stat__value");
  countUp(total, portfolio.totalSolved);
  const toggle = h("input.checkbox", { id: "show-on-board", type: "checkbox", checked: portfolio.showOnLeaderboard });
  toggle.addEventListener("change", async () => {
    toggle.disabled = true;
    try {
      const saved = await api.put("/coding/leaderboard-visibility", { show: toggle.checked });
      portfolio.showOnLeaderboard = saved.show;
      toast(saved.show ? "You now show on the leaderboard" : "You are hidden from the leaderboard");
    } catch (failure) {
      toggle.checked = !toggle.checked;
      toast(messageFor(failure));
    } finally {
      toggle.disabled = false;
    }
  });
  return h(
    "div.stats.stats--3",
    h("div.stat.reveal", total, h("p.stat__label", "Problems solved")),
    h("div.stat.reveal", h("p.stat__value.num", String(portfolio.verifiedAccounts)), h("p.stat__label", "Verified accounts")),
    h(
      "div.stat.reveal",
      h("label.check", { for: "show-on-board" }, toggle, h("span", "Show me on the leaderboard")),
      h("p.row__meta", "Students across the college see your name, class and total. Admins always see your activity."),
      h("a.more", { href: "/student/leaderboard.html" }, "Open the leaderboard", icon("chevron-right")),
    ),
  );
}

function journey() {
  const chart = journeyChart(portfolio.journey);
  return h(
    "section.card",
    cardHead("progress", "Your journey"),
    chart
      ? h("div.coding-chart-wrap", chart)
      : empty("progress", "No journey yet", "Verify an account to start. Your total is saved every day, so the line grows as you solve problems."),
  );
}

function copyButton(code) {
  const button = h("button.button.button--secondary.button--small", { type: "button" }, icon("copy"), "Copy");
  button.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(code);
      toast("Code copied");
    } catch {
      toast("Select the code and copy it.");
    }
  });
  return button;
}

function pendingBody(account) {
  const verify = h("button.button.button--primary.button--small", { type: "button" }, icon("check"), "Verify");
  verify.addEventListener("click", () => run(verify, () => api.post(`/coding/accounts/${account.id}/verify`), "Account verified"));
  return h(
    "div.stack",
    h(
      "ol.coding-steps",
      h("li", "Copy this code:"),
      h("li.coding-code", h("code.num", account.verificationCode), copyButton(account.verificationCode)),
      h("li", `Put it in your ${account.platformName} ${account.codeHint} and save.`),
      h("li", "Press Verify. You can take the code out again afterwards."),
    ),
    h("div.line.line--wrap", verify, h("a.button.button--quiet.button--small", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, icon("external"), "Open your profile")),
  );
}

function verifiedBody(account) {
  const refresh = h("button.button.button--secondary.button--small", { type: "button" }, icon("refresh"), "Refresh");
  const waitUntil = account.refreshAvailableAt ? new Date(account.refreshAvailableAt) : null;
  if (waitUntil && waitUntil > new Date()) {
    refresh.disabled = true;
    refresh.title = "You can refresh again at " + waitUntil.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  refresh.addEventListener("click", () => run(refresh, () => api.post(`/coding/accounts/${account.id}/refresh`), "Updated"));
  return h(
    "div.stack",
    account.rankLabel ? h("p", tag(account.rankLabel, "purple", "target")) : null,
    accountFigures(account),
    account.countsProblems ? null : h("p.row__meta", "GitHub shows your projects; it does not add to the problems solved."),
    syncLine(account),
    h("div.line.line--wrap", refresh),
  );
}

function accountCard(account) {
  const verified = account.status === "VERIFIED";
  const remove = h("button.icon-button.icon-button--bare", { type: "button", "aria-label": `Remove ${account.platformName}` }, icon("trash"));
  remove.addEventListener("click", () => removeAccount(account));
  return h(
    "section.card.coding-account.reveal",
    h(
      "div.card__head",
      h("h2.card__title", icon("code"), account.platformName),
      h("div.line", verified ? tag("Verified", "success", "check-circle") : tag("Waiting for the code", "warning", "clock"), remove),
    ),
    h("a.row__link.coding-handle", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle),
    verified ? verifiedBody(account) : pendingBody(account),
  );
}

function accounts() {
  if (!portfolio.accounts.length) {
    return h("section.card", empty("code", "No accounts linked", "Link GitHub, LeetCode, Codeforces, CodeChef, GeeksforGeeks or HackerRank to build your portfolio.", linkButton()));
  }
  return h("div.coding-grid", portfolio.accounts.map(accountCard));
}

function draw() {
  fill(content, summary(), journey(), accounts());
  reveal();
}

async function run(button, request, message) {
  button.disabled = true;
  try {
    await request();
    toast(message);
    await load();
  } catch (failure) {
    toast(messageFor(failure));
    button.disabled = false;
  }
}

async function removeAccount(account) {
  const sure = await confirmAction({ title: `Remove ${account.platformName}?`, text: `@${account.handle} and its history leave your portfolio. You can link it again later.`, confirmLabel: "Remove", danger: true });
  if (!sure) return;
  try {
    await api.delete(`/coding/accounts/${account.id}`);
    toast("Account removed");
    await load();
  } catch (failure) {
    toast(messageFor(failure));
  }
}

function linkForm() {
  const linked = new Set((portfolio?.accounts ?? []).map((account) => account.platform));
  const free = Object.entries(PLATFORMS).filter(([platform]) => !linked.has(platform));
  if (!free.length) {
    toast("Every site is linked already.");
    return;
  }
  openForm({
    title: "Link an account",
    intro: "The server reads your public profile. Nothing is posted on the site.",
    submitLabel: "Link",
    fields: [
      field("Site", select("platform", free, free[0][0])),
      field("Username", input("handle", { maxlength: 200, required: true, autocomplete: "off", placeholder: "Your username or a link to your profile" }), "As it appears in your profile address."),
    ],
    onSubmit: async (values) => {
      await api.post("/coding/accounts", values);
      toast("Account linked. Now add the code to your profile.");
      await load();
    },
  });
}

function linkButton() {
  return h("button.button.button--primary", { type: "button", onclick: linkForm }, icon("plus"), "Link an account");
}

async function load() {
  await loadInto(content, () => api.get("/coding/portfolio"), (data) => {
    portfolio = data;
    draw();
  });
}

fill(document.getElementById("actions"), linkButton());
load();
