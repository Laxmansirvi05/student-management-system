import { api, LOGIN_PAGE } from "./api.js";

// Shared behaviour of every signed-in page: show who is signed in, and log out.
const userName = document.getElementById("user-name");

export const currentUser = api.get("/auth/me").then((user) => {
  if (userName) {
    userName.textContent = user.displayName;
  }
  return user;
});
currentUser.catch(() => {});

document.getElementById("logout")?.addEventListener("click", async () => {
  await api.post("/auth/logout").catch(() => {});
  window.location.assign(LOGIN_PAGE);
});
