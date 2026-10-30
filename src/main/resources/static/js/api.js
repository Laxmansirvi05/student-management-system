// The one place that talks to the server. Every page uses this module, so the CSRF header,
// expired sessions and error messages are handled the same way everywhere.

const API = "/api/v1";
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

export const LOGIN_PAGE = "/login.html";
export const CHANGE_PASSWORD_PAGE = "/change-password.html";

/** A failed request, in a form the pages can show. `code` is the stable code from the API. */
export class ApiError extends Error {
  constructor({ status, code, detail, errors, traceId, retryAfter, available }) {
    super(detail || "Something went wrong.");
    this.status = status;
    this.code = code;
    this.fieldErrors = errors || [];
    this.traceId = traceId;
    this.retryAfter = retryAfter;
    // Sent with QUIZ_NOT_ENOUGH_QUESTIONS: how many questions the unit has.
    this.available = available;
  }
}

function readCookie(name) {
  const prefix = name + "=";
  const part = document.cookie.split("; ").find((entry) => entry.startsWith(prefix));
  return part ? decodeURIComponent(part.slice(prefix.length)) : null;
}

/** The CSRF token, fetching one from the server the first time it is needed. */
async function csrfToken() {
  if (!readCookie(CSRF_COOKIE)) {
    await fetch(API + "/auth/csrf", { credentials: "same-origin" });
  }
  return readCookie(CSRF_COOKIE);
}

// Browsers refuse a keepalive request whose body passes 64 KB; leave room for the headers.
const KEEPALIVE_MAX_BODY = 60000;

async function request(method, path, body, { redirectOnAuthFailure = true, keepalive = false } = {}) {
  const headers = { Accept: "application/json" };
  const options = { method, headers, credentials: "same-origin" };
  if (method !== "GET") {
    headers[CSRF_HEADER] = await csrfToken();
  }
  if (body instanceof FormData) {
    // The browser writes the multipart content type itself, with the boundary it chose.
    options.body = body;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
    // keepalive lets a save finish after the page is closed. A larger body goes without it.
    if (keepalive && options.body.length < KEEPALIVE_MAX_BODY) {
      options.keepalive = true;
    }
  }

  let response;
  try {
    response = await fetch(API + path, options);
  } catch {
    throw new ApiError({ status: 0, code: "NETWORK", detail: "You appear to be offline." });
  }
  if (response.ok) {
    return response.status === 204 ? null : response.json();
  }

  const problem = await response.json().catch(() => ({}));
  const error = new ApiError({
    status: response.status,
    code: problem.code,
    detail: problem.detail,
    errors: problem.errors,
    traceId: problem.traceId,
    retryAfter: response.headers.get("Retry-After"),
    available: problem.available,
  });

  if (redirectOnAuthFailure) {
    if (error.code === "AUTH_REQUIRED") {
      // The session ended. Say so on the login page instead of failing silently.
      sessionStorage.setItem("sessionExpired", "1");
      window.location.assign(LOGIN_PAGE);
    } else if (error.code === "AUTH_PASSWORD_CHANGE_REQUIRED") {
      window.location.assign(CHANGE_PASSWORD_PAGE);
    }
  }
  throw error;
}

export const api = {
  get: (path, options) => request("GET", path, undefined, options),
  post: (path, body, options) => request("POST", path, body, options),
  put: (path, body, options) => request("PUT", path, body, options),
  patch: (path, body, options) => request("PATCH", path, body, options),
  delete: (path, options) => request("DELETE", path, undefined, options),
  /** Sends a file with its details: a "metadata" part holding JSON and a "file" part. */
  upload(path, metadata, file) {
    const form = new FormData();
    form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
    form.append("file", file);
    return request("POST", path, form);
  },
  /** Sends a form that already holds its parts, for example a file and a path. */
  postForm(path, form, options) {
    return request("POST", path, form, options);
  },
  /** Sends one file as the part "file", for example a CSV of students. */
  postFile(path, file) {
    const form = new FormData();
    form.append("file", file);
    return request("POST", path, form);
  },
  /** Replaces something with one file, sent as the part "file". */
  putFile(path, file) {
    const form = new FormData();
    form.append("file", file);
    return request("PUT", path, form);
  },
};

/** Where a signed-in user belongs. A temporary password always leads to the change page. */
export function homeFor(user) {
  if (user.mustChangePassword) {
    return CHANGE_PASSWORD_PAGE;
  }
  return user.role === "ADMIN" ? "/admin/dashboard.html" : "/student/dashboard.html";
}

// One part of the system is down for now. The server's own words say which part, so they are
// shown. Any other server failure gets the general message with its reference.
const UNAVAILABLE = new Set(["STORAGE_UNAVAILABLE", "AI_UNAVAILABLE", "AI_TIMEOUT", "AI_INVALID_RESPONSE", "CODING_PLATFORM_UNAVAILABLE"]);

/** A message for the user for any failure, in plain language. */
export function messageFor(error) {
  if (error instanceof ApiError) {
    if (!error.message || (error.status >= 500 && !UNAVAILABLE.has(error.code))) {
      const reference = error.traceId ? " Reference: " + error.traceId : "";
      return "Something went wrong on our side. Please try again." + reference;
    }
    return error.message;
  }
  return "Something went wrong. Please try again.";
}
