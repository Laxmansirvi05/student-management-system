import { api, homeFor } from "./api.js";
import { reveal } from "./motion.js";
import "./preferences.js";

// The front door. Someone who is logged in goes straight to their own home page; a visitor sees
// the landing page. Until that is known the page stays hidden, so a logged-in user never sees it
// flash past.

function showLanding() {
  document.body.classList.remove("landing--checking");
  reveal();
}

api
  .get("/auth/me", { redirectOnAuthFailure: false })
  .then((user) => window.location.replace(homeFor(user)))
  .catch(showLanding);
