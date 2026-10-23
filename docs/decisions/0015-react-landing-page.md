# 0015. React and TypeScript for the public landing page only

- Status: Accepted by the owner's decision of 2026-10-23 (React and TypeScript only for the isolated landing page)
- Date: 2026-10-23
- Deciders: Laxman Sirvi
- Changes: an exception to [docs/04-TRD.md](../04-TRD.md) §2 ("Not approved") and [AGENTS.md](../../AGENTS.md) §2 for one page, the public landing page at `/`; records the build tooling that page needs

## Context

The public landing page (FR-UX-002) was rebuilt in commit `4fd019e` with React 19, TypeScript, Vite, Tailwind CSS, three.js (through `@react-three/fiber`) and framer-motion, in its own folder `landing-frontend/`. Its built files are committed into `src/main/resources/static/js/landing/` and `static/css/landing/`, and `static/index.html` loads them.

The TRD forbids any frontend framework, bundler or CSS framework without an accepted decision record. The 2026-10-23 audit (finding CC-03) found none, and found that `StaticFrontendRulesTest` already excused `js/landing` from the frontend rules. This record closes that gap.

## Options considered

1. **Rebuild the landing page in plain HTML, CSS and JavaScript.** Keeps the stack rule without exception, but throws away the owner's finished page, including the 3D globe and the card carousel.
2. **Keep the React page as an isolated exception.** One public page uses React; everything else keeps the approved stack.

## Decision

Option 2. React and TypeScript, built with Vite and styled with Tailwind, are allowed **for the public landing page only**, under these rules:

- **Isolated.** The source lives only in `landing-frontend/`. The built files live only in `static/js/landing/` and `static/css/landing/`, and only `static/index.html` loads them. No student, admin, login, legal or other page may load them, and no other page or module may import React or anything from `landing-frontend/`.
- **Build-time only.** Node.js and npm are development tools for building this page (`npm run build` in `landing-frontend/`). They never run on the server. The application is still one Spring Boot JAR, and the built bundle is committed, so the Maven build, CI and deployment do not need Node.
- **Same security rules at runtime.** The bundle runs under the unchanged Content Security Policy: no inline script, no `eval`, no CDN. It talks to the server only through `GET /api/v1/auth/me`, to send a logged-in visitor on to their home page.
- **Hashed file names.** Each build writes new hash-named files. Only the referenced pair is kept in Git; old builds are deleted. Because of the hash in the name, the server lets browsers keep these files for a year (`StaticResourceCaching`, CC-04).
- **Nothing else changes.** The Java backend, the database, every student and admin page, `js/api.js`, the design tokens and the rules in AGENTS.md §7 stay as they are. This record allows no other use of React, a bundler or Tailwind.

## Trade-offs

- A second toolchain (Node, 189 MB of `node_modules`) must be installed to change the landing page.
- The landing bundle is large (about 1.3 MB, about 370 KB compressed), much more than a plain page would need.
- The owner must be ready to explain in the viva why one page differs: it is a marketing page, separate from the Java application being assessed.

## Consequences

- `StaticFrontendRulesTest` keeps its exemption for `js/landing` and also checks that only `index.html` loads the landing bundle and that no application script imports it.
- `docs/04-TRD.md`, `AGENTS.md` and `README.md` point to this record.
- Rebuilding the landing page: run `npm run build` in `landing-frontend/`, delete the previous hash-named files in `static/js/landing/` and `static/css/landing/`, and commit the new pair together with the updated `static/index.html`.
