import { api, LOGIN_PAGE } from "./api.js";
import "./preferences.js";

// Shared behaviour of every signed-in page: who is signed in, logging out, the phone menu,
// and the small page title that appears in the top bar once the page is scrolled.

/** The first letters of the first and last name, for the round avatar. */
export function initialsOf(fullName) {
  if (!fullName) return "";
  const words = fullName.trim().split(/\s+/);
  const letters = words.length > 1 ? words[0][0] + words[words.length - 1][0] : words[0].slice(0, 2);
  return letters.toUpperCase();
}

function show(id, text) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = text;
  }
}

function applyAvatarPhoto(url) {
  const avatars = ["user-initials", "top-initials"].map((id) => document.getElementById(id)).filter(Boolean);
  for (const avatar of avatars) {
    avatar.classList.add("avatar--photo");
    let img = avatar.querySelector("img");
    if (!img) {
      img = document.createElement("img");
      img.alt = "";
      avatar.replaceChildren(img);
    }
    img.src = url;
  }
}

// Synchronously restore user info from sessionStorage to eliminate any flickering during navigation
try {
  const cachedUserStr = sessionStorage.getItem("cc_user");
  if (cachedUserStr) {
    const cachedUser = JSON.parse(cachedUserStr);
    if (cachedUser.displayName) {
      show("user-name", cachedUser.displayName);
      const initials = initialsOf(cachedUser.displayName);
      for (const id of ["user-initials", "top-initials"]) {
        show(id, initials);
        const avatar = document.getElementById(id);
        if (avatar) avatar.dataset.initials = initials;
      }
    }
    if (cachedUser.email) {
      show("user-email", cachedUser.email);
    }
  }
  const cachedPhoto = sessionStorage.getItem("cc_photo");
  if (cachedPhoto) {
    applyAvatarPhoto(cachedPhoto);
  }
} catch {
  // Session storage unavailable or corrupt; continue gracefully
}

/**
 * Shows the student's photo in both round avatars (sidebar and top bar), or the initials when
 * there is no photo. The profile page calls it after a photo is added or removed, so the two
 * always agree.
 */
export async function showAvatarPhoto(hasPhoto) {
  const avatars = ["user-initials", "top-initials"].map((id) => document.getElementById(id)).filter(Boolean);
  const initials = () => {
    try {
      sessionStorage.removeItem("cc_photo");
    } catch {}
    for (const avatar of avatars) {
      avatar.classList.remove("avatar--photo");
      avatar.textContent = avatar.dataset.initials || avatar.textContent;
    }
  };
  if (!hasPhoto) {
    initials();
    return;
  }
  try {
    const link = await api.get("/students/me/photo-url");
    try {
      sessionStorage.setItem("cc_photo", link.url);
    } catch {}
    for (const avatar of avatars) {
      const image = document.createElement("img");
      image.alt = "";
      image.addEventListener("error", initials, { once: true });
      image.src = link.url;
      avatar.replaceChildren(image);
      avatar.classList.add("avatar--photo");
    }
  } catch {
    // File storage is not reachable: the initials stay.
    initials();
  }
}

export const currentUser = api.get("/auth/me").then((user) => {
  try {
    sessionStorage.setItem("cc_user", JSON.stringify({ displayName: user.displayName, email: user.email }));
  } catch {}
  show("user-name", user.displayName);
  show("user-email", user.email);
  const initials = initialsOf(user.displayName);
  for (const id of ["user-initials", "top-initials"]) {
    show(id, initials);
    const avatar = document.getElementById(id);
    if (avatar) {
      avatar.dataset.initials = initials;
    }
  }
  return user;
});
currentUser.catch(() => {});

/** The student's own profile, or null for an admin. Used for the chip in the top bar. */
export const currentStudent = currentUser.then((user) =>
  user.role === "STUDENT" ? api.get("/students/me") : null,
);
currentStudent
  .then((student) => {
    if (student) {
      showAvatarPhoto(student.hasPhoto);
      // The floating robot lives on every student page, and is loaded only there.
      import("./robot/robot.js").then((robot) => robot.mountRobot()).catch(() => {});
    }
    const chip = document.getElementById("student-chip");
    if (student && chip) {
      chip.textContent = `${student.branch} · Sem ${student.currentSemester} · Sec ${student.section} · ${student.rollNo}`;
      chip.hidden = false;
    }
  })
  .catch(() => {});

for (const button of document.querySelectorAll("[data-logout]")) {
  button.addEventListener("click", async () => {
    try {
      sessionStorage.removeItem("cc_user");
      sessionStorage.removeItem("cc_photo");
    } catch {}
    await api.post("/auth/logout").catch(() => {});
    window.location.assign(LOGIN_PAGE);
  });
}

/**
 * Ensures the active highlight matches the current route reliably across direct visits,
 * clicks, browser back/forward (BFCache), and subpages.
 */
export function syncActiveNavigation() {
  const path = window.location.pathname;

  // Map subpages to their primary nav route
  let targetPath = path;
  if (path.includes("/resource.html")) {
    targetPath = "/student/resources.html";
  } else if (path.includes("/note-shared.html")) {
    targetPath = "/student/notes.html";
  } else if (path.includes("/quiz-history.html")) {
    targetPath = "/student/quiz.html";
  }

  // Handle sidebar nav items
  const navItems = document.querySelectorAll(".nav__item");
  for (const item of navItems) {
    const href = item.getAttribute("href");
    if (!href) continue;
    const isCurrent = targetPath.endsWith(href) || ((targetPath === "/student/" || targetPath === "/student") && href.includes("dashboard.html"));
    if (isCurrent) {
      item.setAttribute("aria-current", "page");
    } else {
      item.removeAttribute("aria-current");
    }
  }

  // Handle usercard active state when on profile
  const isProfile = path.includes("/profile.html");
  const usercard = document.querySelector(".usercard");
  const usercardWho = document.querySelector(".usercard__who");
  if (usercard) {
    usercard.classList.toggle("usercard--active", isProfile);
  }
  if (usercardWho) {
    if (isProfile) {
      usercardWho.setAttribute("aria-current", "page");
    } else {
      usercardWho.removeAttribute("aria-current");
    }
  }

  // Handle mobile sheet links
  const sheetLinks = document.querySelectorAll(".sheet__link");
  for (const link of sheetLinks) {
    const href = link.getAttribute("href");
    if (!href) continue;
    if (path.endsWith(href)) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  }

  // Handle bottom tabbar items
  const tabbarItems = document.querySelectorAll(".tabbar__item");
  for (const item of tabbarItems) {
    const href = item.getAttribute("href");
    if (!href) continue;
    if (targetPath.endsWith(href)) {
      item.setAttribute("aria-current", "page");
    } else {
      item.removeAttribute("aria-current");
    }
  }
}

syncActiveNavigation();

// Pages fade between each other with the browser's cross-page view transitions (base.css). When a
// new navigation interrupts one, the browser rejects its promises; nothing here needs to react, so
// they are settled quietly instead of being reported as uncaught errors.
for (const type of ["pageswap", "pagereveal"]) {
  window.addEventListener(type, (event) => {
    event.viewTransition?.ready.catch(() => {});
    event.viewTransition?.finished.catch(() => {});
    event.viewTransition?.updateCallbackDone?.catch(() => {});
  });
}

// Re-sync on pageshow to handle browser Back/Forward cache navigation
window.addEventListener("pageshow", syncActiveNavigation);

// The phone menu: a sheet that slides up from the tab bar.
const menu = document.getElementById("menu-sheet");
document.getElementById("menu-open")?.addEventListener("click", () => menu.showModal());
menu?.addEventListener("click", (event) => {
  // A click on the dimmed area around the sheet, or on its Close button, closes it.
  if (event.target === menu || event.target.closest("[data-close]")) {
    menu.close();
  }
});

// The top bar shows the page title only after the large title has scrolled out of view.
const topbar = document.querySelector(".topbar");
const heading = document.querySelector(".page h1");
if (topbar && heading && "IntersectionObserver" in window) {
  new IntersectionObserver(
    ([entry]) => topbar.classList.toggle("is-scrolled", !entry.isIntersecting),
    { rootMargin: "-64px 0px 0px 0px" },
  ).observe(heading);
}
