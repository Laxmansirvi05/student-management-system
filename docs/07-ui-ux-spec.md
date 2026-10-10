# 07. UI/UX Specification

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This document fixes the interface rules before any page is built. Screens are listed in [06-app-flow.md](06-app-flow.md).

## 1. Design principles

1. **Answer first.** The number a student came for (attendance, score, CGPA) is the largest thing on the screen.
2. **Phone first.** Students use phones. Every student screen works at 360 px wide before it is widened.
3. **Calm and plain.** One accent colour, generous spacing, no decoration that carries no meaning.
4. **Honest states.** Loading, empty and error always look different from each other and from real data.
5. **Same thing, same place.** Navigation, page titles, primary buttons and messages sit in the same position on every screen.

## 2. Visual direction

Clean academic look: white surfaces on a light grey background, a deep blue accent, rounded cards with a hairline border. The admin portal uses the same components with a denser layout.

## 3. Colour tokens

Defined once as CSS custom properties in `css/tokens.css`. Components use tokens, never raw colour values.

| Token | Value | Use |
| --- | --- | --- |
| `--color-bg` | `#F5F7FA` | Page background |
| `--color-surface` | `#FFFFFF` | Cards, tables, dialogs |
| `--color-border` | `#D9DEE7` | Borders and dividers |
| `--color-text` | `#1B2430` | Main text |
| `--color-text-muted` | `#5B6676` | Secondary text |
| `--color-primary` | `#1F4FD8` | Primary buttons, links, active navigation |
| `--color-primary-hover` | `#183FB0` | Hover and pressed |
| `--color-on-primary` | `#FFFFFF` | Text on primary |
| `--color-success` | `#1A7F4B` | Success, "Strong" |
| `--color-warning` | `#9A6700` | Warnings, "Improving" |
| `--color-danger` | `#C0262D` | Errors, "Below required", "Needs revision" |
| `--color-info-bg` | `#EAF0FE` | Informational banners, AI label |
| `--color-focus` | `#1F4FD8` | Focus ring |

Every text and background pair MUST meet WCAG AA contrast (4.5:1 for body text, 3:1 for large text and control borders). Check each pair with a contrast tool when the stylesheet is written; adjust the value, not the rule, if one fails.

Status is never shown by colour alone. It always has a text label, and an icon where space allows.

## 4. Typography

System font stack, so no font files are downloaded:

```
font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif;
```

| Token | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `--text-display` | 32 / 40 px | 700 | Headline figures on cards |
| `--text-h1` | 24 / 32 px | 700 | Page title |
| `--text-h2` | 18 / 26 px | 600 | Section and card titles |
| `--text-body` | 16 / 24 px | 400 | Body text, inputs |
| `--text-small` | 14 / 20 px | 400 | Table cells, helper text |
| `--text-caption` | 12 / 16 px | 500 | Labels, badges |

Body text is never smaller than 14 px. Figures in tables use tabular numerals.

## 5. Spacing, sizing and shape

- Spacing scale: 4, 8, 12, 16, 24, 32, 48 px (`--space-1` to `--space-7`).
- Corner radius: 8 px for cards and dialogs, 6 px for inputs and buttons, 999 px for badges.
- Touch targets are at least 44 × 44 px.
- Content width is capped at 1120 px and centred.
- Cards have 16 px padding on phones and 24 px from tablet width.

## 6. Reusable components

| Component | Rules |
| --- | --- |
| Button | Primary (filled), secondary (outline), danger (filled red), text. One primary button per screen. Shows a spinner and is disabled while its request runs. |
| Card | Title, optional action link, body. Has its own loading, empty and error state. |
| Stat card | Label, one large figure, one line of context ("3 subjects below 75%"). |
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
