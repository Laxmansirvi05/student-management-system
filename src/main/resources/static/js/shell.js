import { api, LOGIN_PAGE } from "./api.js";
import "./preferences.js";

// Shared behaviour of every signed-in page: who is signed in, logging out, the phone menu,
// and the small page title that appears in the top bar once the page is scrolled.

/** The first letters of the first and last name, for the round avatar. */
export function initialsOf(fullName) {
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

/**
 * Shows the student's photo in both round avatars (sidebar and top bar), or the initials when
 * there is no photo. The profile page calls it after a photo is added or removed, so the two
 * always agree.
 */
export async function showAvatarPhoto(hasPhoto) {
  const avatars = ["user-initials", "top-initials"].map((id) => document.getElementById(id)).filter(Boolean);
  const initials = () => {
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
  show("user-name", user.displayName);
  show("user-email", user.email);
  for (const id of ["user-initials", "top-initials"]) {
    show(id, initialsOf(user.displayName));
    const avatar = document.getElementById(id);
    if (avatar) {
      avatar.dataset.initials = initialsOf(user.displayName);
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
    await api.post("/auth/logout").catch(() => {});
    window.location.assign(LOGIN_PAGE);
  });
}

// The sidebar's blue highlight is one element placed behind the current page's item. Each page puts
// it in its own spot, and the browser glides it from the old spot to the new one between pages.
function placeNavPill() {
  const nav = document.querySelector(".nav");
  const active = nav?.querySelector('.nav__item[aria-current="page"]');
  if (!nav || !active) {
    return;
  }
  const pill = document.createElement("span");
  pill.className = "nav__pill";
  pill.setAttribute("aria-hidden", "true");
  pill.style.top = active.offsetTop + "px";
  pill.style.height = active.offsetHeight + "px";
  nav.prepend(pill);
  nav.classList.add("nav--pill");
}
placeNavPill();

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
