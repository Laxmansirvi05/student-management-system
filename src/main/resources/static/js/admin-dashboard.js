import { currentUser } from "./shell.js";

// The admin home page: a greeting and a link to every admin screen.
currentUser
  .then((user) => {
    document.getElementById("greeting").textContent = "Welcome, " + user.displayName;
  })
  .catch(() => {});
