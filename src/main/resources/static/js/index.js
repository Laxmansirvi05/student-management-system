import { api, homeFor } from "./api.js";

// The front door: send a signed-in user to the right place. A visitor never gets this far,
// because the server redirects them to the login page first.
api
  .get("/auth/me")
  .then((user) => window.location.replace(homeFor(user)))
  .catch(() => {});
