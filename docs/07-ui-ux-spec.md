# 07. UI/UX Specification

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-13

This document fixes the interface rules before any page is built. Screens are listed in [06-app-flow.md](06-app-flow.md).

## 1. Design principles

1. **Answer first.** The number a student came for (attendance, score, CGPA) is the largest thing on the screen.
2. **Phone first.** Students use phones. Every student screen works at 360 px wide before it is widened.
3. **Calm and premium.** Black, white and greys do almost all the work. Blue is the one accent. Generous white space, large clear type, and no decoration that carries no meaning.
4. **Honest states.** Loading, empty and error always look different from each other and from real data.
5. **Same thing, same place.** Navigation, page titles, primary buttons and messages sit in the same position on every screen.

## 2. Visual direction

Decided by the owner on 2026-10-13: a premium, minimal look in the spirit of Apple's interfaces. Mostly black and white, with blue as the single complementary colour.

What that means in practice:

- **Monochrome first.** Pages are white with near-black text. Hierarchy comes from size, weight and spacing, not from colour.
- **Blue is rare, so it means something.** It marks the one primary action on a screen, links, the active navigation item and the focus ring. Nothing decorative is blue.
- **Black for emphasis.** Headline figures and page titles are near-black and large. Secondary actions are black outlines or plain text, never a second colour.
- **Space instead of boxes.** Sections are separated by white space and a pale grey band, not by heavy borders. Cards have a hairline border or a very soft shadow, never both.
- **Soft, large corners** and unhurried, short transitions (150 to 200 ms) on hover, focus and page elements appearing.
- **Clean type.** The system font, which is San Francisco on Apple devices, with tight headings and comfortable body text.
- **No clutter.** No gradients, no patterned backgrounds, no coloured section headers, no icons used as decoration.

This is a look and feel, not a copy. The interface must not use Apple's logo, product imagery, icons or wording, or present itself as an Apple product.

The admin portal uses the same components with a denser layout.

## 3. Colour tokens

Defined once as CSS custom properties in `css/tokens.css`. Components use tokens, never raw colour values.

### Light theme (default)

| Token | Value | Use |
| --- | --- | --- |
| `--color-bg` | `#FFFFFF` | Page background |
| `--color-bg-subtle` | `#F5F5F7` | Alternating section bands, table header, input background |
| `--color-surface` | `#FFFFFF` | Cards, dialogs |
| `--color-border` | `#D2D2D7` | Hairline borders and dividers. Decorative only: never the only edge of a control |
| `--color-border-control` | `#86868B` | The edge of inputs and other controls, which needs 3:1 contrast |
| `--color-text` | `#1D1D1F` | Main text, headings, headline figures |
| `--color-text-muted` | `#6E6E73` | Secondary text, captions |
| `--color-inverse-bg` | `#000000` | The footer, and the header on the login page |
| `--color-inverse-text` | `#F5F5F7` | Text on black |
| `--color-primary` | `#0071E3` | Primary button, active navigation |
| `--color-primary-hover` | `#0077ED` | Hover |
| `--color-primary-pressed` | `#006EDB` | Pressed |
| `--color-on-primary` | `#FFFFFF` | Text on a primary button |
| `--color-link` | `#0066CC` | Text links |
| `--color-focus` | `#0071E3` | Focus ring |
| `--color-info-bg` | `#F5F5F7` | Informational banners, the AI-generated label |

### Status colours

Black, white and blue cannot signal success or danger, so three muted status colours are kept. They are used only for status, in small amounts: a badge, an icon, a thin left edge on a banner.

| Token | Value | Use |
| --- | --- | --- |
| `--color-success` | `#1A7F4B` | Success, "Strong" |
| `--color-warning` | `#9A6700` | Warnings, "Improving" |
| `--color-danger` | `#C0262D` | Errors, "Below required", "Needs revision" |

Status is never shown by colour alone. It always has a text label, and an icon where space allows.

### Dark theme (P1)

A black theme that follows the device setting (`prefers-color-scheme`). The token names stay the same; only the values change, so no component is rewritten. It is built after the first release.

| Token | Value |
| --- | --- |
| `--color-bg` | `#000000` |
| `--color-bg-subtle` | `#1D1D1F` |
| `--color-surface` | `#1D1D1F` |
| `--color-border` | `#424245` |
| `--color-text` | `#F5F5F7` |
| `--color-text-muted` | `#A1A1A6` |
| `--color-primary` | `#0A84FF` |
| `--color-link` | `#2997FF` |

### Contrast

Every text and background pair MUST meet WCAG AA contrast (4.5:1 for body text, 3:1 for large text and for the edges of controls). Measured in task `T1-09`:

| Pair | Ratio | Needs |
| --- | --- | --- |
| Text on page / on subtle background | 16.83 / 15.46 | 4.5 |
| Muted text on page / on subtle background | 5.07 / 4.66 | 4.5 |
| White on the primary button | 4.70 | 4.5 |
| Link on page | 5.57 | 4.5 |
| Danger text on page / on subtle background | 5.90 / 5.42 | 4.5 |
| Success text on subtle background | 4.61 | 4.5 |
| Warning text on page | 4.87 | 4.5 |
| Light text on black | 19.29 | 4.5 |
| Control edge (`--color-border-control`) on page / on subtle background | 3.62 / 3.33 | 3.0 |
| Focus ring on page | 4.70 | 3.0 |

Two rules follow from the measurements:

- `--color-border` (1.51 on white) is too faint to be the edge of an input. It was the input border in the first draft; inputs now use `--color-border-control`.
- `--color-primary` on the subtle background is 4.31, below 4.5. Blue text is therefore used only on the white page background, never on a grey band.

If a new pair is introduced, measure it first; if it fails, adjust the value, not the rule.

## 4. Typography

System font stack, so no font files are downloaded:

```
font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif;
```

| Token | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `--text-display` | 40 / 44 px | 600 | Headline figures on cards |
| `--text-h1` | 28 / 34 px | 600 | Page title |
| `--text-h2` | 18 / 26 px | 600 | Section and card titles |
| `--text-body` | 16 / 24 px | 400 | Body text, inputs |
| `--text-small` | 14 / 20 px | 400 | Table cells, helper text |
| `--text-caption` | 12 / 16 px | 500 | Labels, badges |

Body text is never smaller than 14 px. Figures in tables use tabular numerals. Headings use slightly tightened letter spacing (about -0.01em); body text uses the default. Weight 600, not 700, keeps large headings refined instead of heavy.

## 5. Spacing, sizing and shape

- Spacing scale: 4, 8, 12, 16, 24, 32, 48 px (`--space-1` to `--space-7`).
- Corner radius: 18 px for cards and dialogs, 12 px for inputs, 999 px (a full pill) for buttons and badges.
- Elevation: cards use either a hairline border or one soft shadow (`0 2px 12px rgba(0, 0, 0, 0.06)`), never both. Dialogs use a stronger shadow.
- Motion: 150 to 200 ms ease-out on hover, focus and appearing elements. No motion when the device asks for reduced motion.
- Touch targets are at least 44 × 44 px.
- Content width is capped at 1120 px and centred.
- Cards have 20 px padding on phones and 32 px from tablet width. Sections are separated by at least 48 px.

## 6. Reusable components

| Component | Rules |
| --- | --- |
| Button | Pill-shaped. Primary (filled blue), inverse (filled black, used for the one action on the sign-in and change-password cards), secondary (black outline on white), danger (filled red), text (blue, no border). One primary or inverse button per screen. Shows a spinner and is disabled while its request runs. |
| Card | Title, optional action link, body. Has its own loading, empty and error state. |
| Stat card | Small muted label, one large near-black figure in the display size, one line of muted context ("3 subjects below 75%"). No coloured background. |
| Badge | Short status text with colour: Strong, Improving, Needs revision, Below required, Draft, Published. |
| Form field | Visible label above the input, optional helper text below, error text below in danger colour. |
| Table | Header row, zebra-free rows with dividers, right-aligned numbers. See responsive rules. |
| Dialog | For confirmation of destructive or irreversible actions only. Title states the action; buttons name the action ("Delete resource", "Cancel"). |
| Toast | Success confirmations, bottom centre on phones and top right on desktop, auto-dismiss after 5 seconds. Errors are not shown as toasts. |
| Banner | Page-level errors and warnings, with a Retry action where it applies. |
| Pagination | Previous, page indicator ("Page 2 of 7"), Next. |
| Filter bar | Dropdowns and a search box above a list; collapses into a "Filters" button on phones. |

## 7. Navigation

**Student:** a bottom tab bar on phones with five items: Home, Academics, Resources, Quiz, Assistant. "Academics" opens Attendance, Results, Timetable and Notices. Profile, Bookmarks, Progress and Log out are in a menu from the header. From tablet width the same items become a left sidebar.

**Admin:** a left sidebar grouped as People (Students), Academics (Subjects, Attendance, Marks, Results, Timetable), Content (Resources, Question bank, Notices). On phones it collapses behind a menu button.

The header shows the product name, the page title, the user's name, and a small "Admin" label for administrators so the two portals are never confused.

## 8. Breakpoints

| Name | Width | Layout |
| --- | --- | --- |
| Phone | under 600 px | One column, bottom tabs |
| Tablet | 600 to 1023 px | Two-column card grid, sidebar |
| Desktop | 1024 px and up | Three-column card grid, sidebar |

## 9. Dashboard layout

Cards in this order, which is also the phone order from top to bottom:

1. Attendance (overall percentage, subjects below required)
2. Results (latest SGPA, CGPA)
3. Today's classes
4. Upcoming deadlines
5. Recent notices
6. Revision recommendations

Each card links to its full page.

## 10. States

| State | Rule |
| --- | --- |
| Loading | Skeleton placeholders shaped like the content, shown if the request takes more than 300 ms. |
| Empty | A short sentence saying what is missing and, where possible, an action. Neutral colours. |
| Error | A banner or in-card message saying what failed, a Retry button, and the reference code for server errors. |
| Success | A toast for saves. The changed data is visible on the page. |
| Disabled | Lower contrast and a reason available nearby ("Select a unit first"). |

Wording rules: plain language, sentence case, no blame, no technical terms, no error codes except the reference. Standard messages are listed in [06-app-flow.md](06-app-flow.md#11-common-failure-handling).

## 11. Forms

- Labels are always visible; placeholders are never the only label.
- Required fields are marked "(required)" in the label.
- Validation runs on submit, then on change for fields already marked invalid.
- On failure, focus moves to the first invalid field and the form keeps all values.
- Destructive actions need a confirmation dialog.
- The admin attendance and marks sheets are editable tables with one Save button and an unsaved-changes warning on leaving.

### Sign-in and change-password pages

Built in task `T1-09`, following a reference video supplied by the owner on 2026-10-13.

- One centred card with large rounded corners and a soft shadow, on the pale grey background. From 760 px wide it is split in two halves; below that the black half sits on top.
- **Black half:** the Campus Companion mascot, the product name and one line of text. A soft light follows the pointer.
- **White half:** a small person icon, the heading, the fields and one black pill button.
- **The mascot** is the robot from the reference video, as the owner asked on 2026-10-13: a white outline of an eight-sided helmet with two ear pieces and a bent antenna, one large eye marked with a cross and one small square eye. It is redrawn by hand as an inline SVG; no file from the video is used. Its face turns toward the pointer and its eyes become two flat lines while a password field has focus. It is decorative: hidden from screen readers, and it does not move when the device asks for reduced motion.
- **Deliberately absent**, because the requirements forbid them: sign in with Google, Apple or any other provider; "Sign up"; "Remember me"; a self-service "Forgot password?" link. In their place is one line: "Forgot your password? Ask your college administrator to reset it."
- The login form has two labelled fields, "Institutional email" and "Password", with a show-password button.

## 12. Responsive tables

On phones a table becomes a list of stacked cards, one per row, with each cell shown as "label: value". Wide admin sheets (attendance, marks) scroll horizontally inside their container with the student name column fixed. The page itself never scrolls sideways.

## 13. PDF viewing

- The resource page shows the PDF in an `<iframe>` using the signed link, at least 70% of the viewport height.
- "Open in new tab" and "Download" are always offered, because some phone browsers do not render PDFs inline.
- If the link has expired when the user returns, the page requests a new one.

## 14. AI assistant

- Three tabs: Explain, Summarize, Practice questions. Each is a form with a result area below; it is not a free-form chat.
- A character counter under each text area shows the limit.
- While waiting: the button is disabled, a progress message says "This can take up to 30 seconds", and the form is locked until the response arrives.
- Every result carries a visible label: "AI-generated. Check it against your course material." and a line stating its basis ("Based on general knowledge" or "Based on the text you provided").
- The requests remaining today are shown near the button.
- Results are rendered as plain text and lists. No HTML from the AI is ever interpreted.

## 15. Quiz

- One question per screen on phones, with Previous and Next and a question grid showing answered and unanswered.
- The timer is fixed at the top. At one minute remaining it changes to the warning colour and announces the change to screen readers once.
- Submit asks for confirmation and states how many questions are unanswered.
- The result screen shows the score first, then each question with the chosen answer, the correct answer and the explanation. Correct and incorrect are marked with text and icon, not only colour.

## 16. Flowcharts (P1)

Rendered with Mermaid from a definition produced by the backend. The diagram scrolls inside its container on phones and has a text list of the steps beneath it for accessibility.

## 17. Accessibility

Target: WCAG 2.1 level AA.

- Every page has one `<h1>`, a logical heading order, and landmark elements (`header`, `nav`, `main`).
- Everything works with a keyboard alone, in a sensible tab order, with a visible focus ring.
- Every input has a `<label>`. Errors are linked with `aria-describedby`.
- Icons that carry meaning have text alternatives. Decorative icons are hidden from assistive technology.
- Toasts and inline results use `aria-live="polite"`. Errors use `role="alert"`.
- Dialogs trap focus and return it to the trigger on close.
- Pages remain usable at 200% zoom and respect `prefers-reduced-motion`.
