import { currentUser } from "./shell.js";

currentUser
  .then((user) => {
    document.getElementById("greeting").textContent = "Welcome, " + user.displayName;
  })
  .catch(() => {});
