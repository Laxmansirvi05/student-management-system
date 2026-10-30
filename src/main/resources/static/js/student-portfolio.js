import { confirmAction, field, input, openForm, select } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { renderDashboard } from "./coding-dashboard.js";
import { PLATFORMS } from "./coding-parts.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// The student's coding portfolio (FR-COD-001 to FR-COD-004, ADR 0017). The dashboard is drawn by
// coding-dashboard.js; this page adds what only the owner can do: link a site, verify it with the
// code, refresh, remove, and show or hide themselves on the leaderboard.

const content = document.getElementById("content");
const state = { selected: "ALL" };
let portfolio = null;

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

function pendingBody(account) {
  const verify = h("button.button.button--primary", { type: "button" }, icon("check"), "Verify");
  verify.addEventListener("click", () => run(verify, () => api.post(`/coding/accounts/${account.id}/verify`), "Account verified"));
  return h(
    "div.stack",
    h("a.row__link", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, "@" + account.handle),
    h(
      "ol.coding-steps",
      h("li", "Copy this code:"),
      h("li.coding-code", h("code.num", account.verificationCode), copyButton(account.verificationCode)),
      h("li", `Put it in your ${account.platformName} ${account.codeHint} and save.`),
      h("li", "Press Verify. You can take the code out again afterwards."),
    ),
    h("div.line.line--start.line--wrap", verify, h("a.button.button--quiet", { href: account.profileUrl, target: "_blank", rel: "noopener noreferrer" }, icon("external"), "Open your profile"), removeButton(account)),
  );
}

function canRefresh(account) {
  return account.status === "VERIFIED" && (!account.refreshAvailableAt || new Date(account.refreshAvailableAt) <= new Date());
}

function accountActions(account) {
  const refresh = h("button.button.button--secondary.button--small", { type: "button", disabled: !canRefresh(account) }, icon("refresh"), "Refresh");
  if (!canRefresh(account)) {
    refresh.title = "You can refresh again at " + new Date(account.refreshAvailableAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  refresh.addEventListener("click", () => run(refresh, () => api.post(`/coding/accounts/${account.id}/refresh`), "Updated"));
  return h("div.line.line--start.line--wrap", refresh, removeButton(account));
}

function removeButton(account) {
  const remove = h("button.button.button--quiet.button--small", { type: "button" }, icon("trash"), "Remove");
  remove.addEventListener("click", async () => {
    const sure = await confirmAction({ title: `Remove ${account.platformName}?`, text: `@${account.handle} and its history leave your portfolio. You can link it again later.`, confirmLabel: "Remove", danger: true });
    if (!sure) return;
    try {
      await api.delete(`/coding/accounts/${account.id}`);
      toast("Account removed");
      state.selected = "ALL";
      await load();
    } catch (failure) {
      toast(messageFor(failure));
    }
  });
  return remove;
}

function headerControls(data) {
  const toggle = h("input.cd-switch", { id: "show-on-board", type: "checkbox", role: "switch", checked: data.showOnLeaderboard });
  toggle.addEventListener("change", async () => {
    toggle.disabled = true;
    try {
      const saved = await api.put("/coding/leaderboard-visibility", { show: toggle.checked });
      data.showOnLeaderboard = saved.show;
      toast(saved.show ? "You now show on the leaderboard" : "You are hidden from the leaderboard");
    } catch (failure) {
      toggle.checked = !toggle.checked;
      toast(messageFor(failure));
    } finally {
      toggle.disabled = false;
    }
  });
  const refreshAll = h("button.cd-control__button", { type: "button" }, icon("refresh"), h("span", "Refresh now"));
  refreshAll.addEventListener("click", () => refreshEverything(refreshAll));
  return h(
    "div.cd-controls",
    h("label.cd-control", { for: "show-on-board" }, h("span", icon("users"), " Show me on the leaderboard"), toggle),
    h("div.cd-control", refreshAll),
  );
}

/** Refreshes every verified site whose waiting time is over, one after the other. */
async function refreshEverything(button) {
  const due = portfolio.accounts.filter(canRefresh);
  if (!due.length) {
    toast("Everything was refreshed in the last 15 minutes.");
    return;
  }
  button.disabled = true;
  let done = 0;
  const failed = [];
  for (const account of due) {
    try {
      await api.post(`/coding/accounts/${account.id}/refresh`);
      done++;
    } catch {
      failed.push(account.platformName);
    }
  }
  toast(failed.length ? `Updated ${done}; ${failed.join(", ")} did not answer.` : `Updated ${done} ${done === 1 ? "site" : "sites"}`);
  await load();
}

function addPlatform() {
  const button = h("button.button.button--secondary.cd-add", { type: "button" }, icon("plus"), "Add platform");
  button.addEventListener("click", linkForm);
  return button;
}

function footer(data) {
  return h("a.more.cd-profile__more", { href: "/student/leaderboard.html" }, data.showOnLeaderboard ? "See your rank on the leaderboard" : "Open the leaderboard", icon("chevron-right"));
}

function linkForm() {
  const linked = new Set(portfolio.accounts.map((account) => account.platform));
  const free = Object.entries(PLATFORMS).filter(([platform]) => !linked.has(platform));
  if (!free.length) {
    toast("Every site is linked already.");
    return;
  }
  openForm({
    title: "Add a platform",
    intro: "The server reads your public profile. Nothing is posted on the site.",
    submitLabel: "Link",
    fields: [
      field("Site", select("platform", free, free[0][0])),
      field("Username", input("handle", { maxlength: 200, required: true, autocomplete: "off", placeholder: "Your username or a link to your profile" }), "As it appears in your profile address."),
    ],
    onSubmit: async (values) => {
      const account = await api.post("/coding/accounts", values);
      state.selected = account.platform;
      toast("Linked. Now add the code to your profile.");
      await load();
    },
  });
}

async function photoUrl() {
  const link = await api.get("/students/me/photo-url");
  return link.url;
}

const options = { headerControls, addPlatform, footer, accountActions, pendingBody, photoUrl };

async function load() {
  await loadInto(content, () => api.get("/coding/portfolio"), (data) => {
    portfolio = data;
    renderDashboard(content, data, options, state);
  });
}

fill(document.getElementById("actions"), h("button.button.button--primary", { type: "button", onclick: () => linkForm() }, icon("plus"), "Add platform"));
load();
