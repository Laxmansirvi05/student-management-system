# Design prompts for Google Stitch (student side)

> Status: Working aid · Owner: Laxman Sirvi · Created: 2026-10-13

How to use this file:

1. Start a new Stitch project. Paste **Prompt 0** first. It sets the style and the shared layout.
2. Then paste the screen prompts **one at a time**, in order. For each, ask for a desktop version (1440 px) and a phone version (390 px).
3. If a screen comes out off-style, reply in Stitch with: "Keep the content. Restyle it to match the first screen exactly."
4. When you are happy, share the screenshots (PNG) of each screen with the coding agent. The agent rebuilds them by hand in the project's plain HTML and CSS; Stitch's exported code is not used, because the project allows no frameworks or CDN libraries.

Rules that every design must respect (they come from the PRD and the security rules):

- Students **cannot edit** attendance, marks, results, timetable, notices or their profile. No "Edit" buttons on those.
- No sign-up, no social login, no "Remember me", no profile photo upload, no chat with other students.
- Only made-up people: "Demo Student One", roll number `23CSE001`, email `23cse001@university.example`. No real names or photos.
- The login and change-password pages are already built. Do not redesign them.

---

## Prompt 0: style and shared layout

```
Design a web app called "Campus Companion", a student portal for a college. Create the design system and the shared page layout first; I will ask for each screen after this.

STYLE
- Premium, calm, Apple-like. Mostly black and white, with one accent colour: blue #0071E3. Use blue only for the main action on a screen, links, the active navigation item and key numbers.
- Page background #F5F5F7. Cards are white with 18px rounded corners and one very soft shadow, no borders. Text #1D1D1F, secondary text #6E6E73.
- Each main page may open with one black "hero" card (28px corners) with a soft blue glow behind white text. Everything below it is light.
- Font: system UI / SF Pro style. Big tight headings (40px, semibold), 16px body, small uppercase labels with letter spacing.
- Lots of white space. Thin line icons, 1.7px stroke. Pill-shaped buttons and chips.
- Status is never shown by colour alone: always add a word or icon. Green #1A7F4B for good, amber #9A6700 for warning, red #C0262D for problems.
- Light theme only.

SHARED LAYOUT (desktop, 1440px wide)
- Left sidebar, 260px, white, with the product name "Campus Companion" and a small blue rounded-square logo mark at the top.
- Sidebar groups with small uppercase headings and an icon beside every item:
  - (no heading): Home
  - ACADEMICS: Attendance, Results, Timetable, Notices
  - LEARN: Resources, Bookmarks, Quick Exam, Progress, AI Assistant
- The active item has a pale grey rounded background and a blue icon.
- Bottom of the sidebar: a small card with a round avatar showing the initials "DO", the name "Demo Student One", the email "23cse001@university.example", and a "Log out" text button. Clicking the card opens the Profile page.
- Top bar: frosted white, with the page title on the left. Nothing else.

SHARED LAYOUT (phone, 390px wide)
- No sidebar. A bottom tab bar with five items and icons: Home, Academics, Resources, Quiz, Assistant.
- Top bar with the product name and a round "DO" avatar that opens Profile.

Use only this fictional student everywhere: Demo Student One, roll number 23CSE001, branch CSE, semester 3, section A, admitted 2023. No real names, no photographs of people.

Show me the layout with an empty content area so I can confirm the style.
```

---

## Prompt 1: Dashboard (Home)

```
Using the same style and layout, design the Home dashboard.

Top: a black hero card saying "Good morning, Demo Student One" with one line under it: "CSE · Semester 3 · Section A", and today's date.

Below it, six cards in a grid (3 columns on desktop, 1 column on phone), in this order. Each card has a title, its content and a small "View all" link:
1. Attendance: a large "82.50%" overall figure with a thin ring chart, and a line "1 subject below 75%" with a warning icon.
2. Results: "SGPA 7.80" (latest semester) and "CGPA 7.12", shown as two large numbers.
3. Today's classes: a short timeline of 3 classes with time, subject and room, for example "09:00 – 10:00 · Data Structures · Room 204".
4. Upcoming deadlines: 3 rows with a title, a category chip (Assignment or Exam) and a due date.
5. Recent notices: 3 rows with a title, a category chip and a date.
6. Revision recommendations: 2 rows like "Revise Unit 2: Linked Lists (Data Structures)" with the reason "Scored 40% in your last test".

Also show the empty version of one card: grey text "No classes today" with no chart.
The dashboard is read-only: no edit buttons.
```

## Prompt 2: Attendance

```
Design the Attendance page.

Top: a black hero card with the overall attendance "82.50%" in very large type, a ring chart, and the text "Required: 75%".

Below: a list of subject cards (2 columns on desktop, 1 on phone). Each shows: subject name and code (for example "Data Structures · CS301"), "Classes held 40", "Attended 33", the percentage "82.50%" with a thin progress bar, and a status chip.
- At or above 75%: a green chip with a tick, "On track".
- Below 75%: a red chip with a warning icon, "Below required", for example 70.00%.
- A subject with no classes yet shows grey text "No classes recorded yet" instead of 0%.

When a subject is selected, show its history on the right (desktop) or on a new screen (phone): a list of dated rows, newest first, each with the date, the period number and "Present" (tick icon) or "Absent" (cross icon).

Read-only. No edit or "mark attendance" buttons.
```

## Prompt 3: Marks and results

```
Design the "Results" page with two tabs: "Marks" and "Semester results".

MARKS tab: a semester selector at the top. Then one card per subject. Inside each card, a table of assessments with columns: Assessment (for example "Internal 1", "Assignment 1", "Lab 1"), Type, Marks ("41.50 / 50"), and Percentage. If the student was absent, show the word "Absent" instead of marks. Empty state: "Marks have not been published yet."

SEMESTER RESULTS tab: a black hero card with "CGPA 7.12" in very large type and the caption "Calculated from your published semesters". Under it a table or row of cards, one per semester: Semester number, SGPA, Credits registered, Credits earned, and a status chip: "Pass" (green, tick), "Fail" (red, cross) or "Withheld" (amber, pause icon). Empty state: "Results not published yet."

Read-only.
```

## Prompt 4: Timetable

```
Design the Timetable page.

Desktop: a weekly grid, Monday to Saturday as columns and time down the side. Each class is a rounded block with the subject name, time, room and faculty name, for example "Data Structures · 09:00 – 10:00 · Room 204 · Prof. Demo Faculty". Today's column is highlighted with a pale blue background, and the class happening now has a blue outline and a small "Now" chip.

Phone: a row of day chips (Mon to Sat) at the top with today selected, and a vertical list of that day's classes as cards.

Empty state for a day: "No classes on this day."
Read-only.
```

## Prompt 5: Notices

```
Design the Notices page.

Top: filter chips: All, General, Exam, Assignment, Event.
Below: a list of notice cards, newest first. Each shows a category chip, the title, the first two lines of the text, the published date, and, if it has a deadline, a line "Due 20 Oct 2026" with a clock icon. A deadline within 3 days shows an amber "Due soon" chip; a past deadline shows a grey "Past due" chip.

Selecting a notice opens its detail: title, category, published date, due date, and the full plain text.

Empty state: "No notices yet."
Read-only. No comment box, no reactions.
```

## Prompt 6: Resource hub

```
Design the Resources page, a library of study PDFs.

Top: a large search box "Search notes, papers and manuals", and a filter bar with dropdowns: Subject, Unit, Type (Notes, Syllabus, Lab manual, Question paper, Model paper, Important questions). On phone the filters collapse into one "Filters" button.

Below: a grid of resource cards (3 columns desktop, 1 phone). Each card shows a PDF icon, the title, the subject and unit ("Data Structures · Unit 2"), a type chip, the file size and upload date, and a bookmark icon button in the corner (outline when not saved, filled blue when saved).

At the bottom: pagination with "Previous", "Page 2 of 7", "Next".

Empty state: "No resources match your filters" with a "Clear filters" button.
Students cannot upload: no upload button.
```

## Prompt 7: Resource detail and PDF preview

```
Design the Resource detail page.

Top: a back link "Resources", the title, a row of details (subject, unit, type chip, file size, upload date), and three buttons: "Download" (blue, main), "Open in new tab" (outline) and a bookmark toggle.

Below: a large PDF preview area taking at least 70% of the screen height, inside a rounded card.

Show a second state where the preview could not load: a message "The preview could not be shown. You can still download the file." with the Download button.
```

## Prompt 8: Bookmarks

```
Design the Bookmarks page: the same resource cards as the Resources page, showing only saved items, each with a filled blue bookmark icon that removes it.

Empty state: an outline bookmark icon, "No bookmarks yet", and a button "Browse resources".
```

## Prompt 9: Quick Exam (three screens)

```
Design the Quick Exam feature as three screens.

SCREEN A, Set up: a card titled "Start a quick test" with dropdowns for Subject and Unit, a choice of number of questions (5, 10, 15, 20) as pill buttons, and a line "Time: 1 minute per question". One blue button "Start test". If there are not enough questions, show the message "Not enough questions for this unit yet."

SCREEN B, Taking the test: a timer fixed at the top ("08:42"), which turns amber in the last minute. One multiple-choice question at a time with four large option cards (A to D); the chosen one has a blue outline. "Previous" and "Next" buttons. A question grid (numbered circles) showing answered (filled) and unanswered (outline). A "Submit" button. Also show the confirmation dialog: "Submit your test? 2 questions are unanswered." with "Keep going" and "Submit".

SCREEN C, Result: a black hero card with the score "7 / 10" and "70%". Below, each question with the student's answer, the correct answer and a short explanation. Correct answers are marked with a green tick and the word "Correct"; wrong ones with a red cross and the word "Incorrect"; skipped ones with a grey dash and "Not answered".
```

## Prompt 10: Attempt history

```
Design the "Test history" page: a table (cards on phone) of past quick tests with columns: Date, Subject, Unit, Score ("7 / 10"), Percentage, and a "View" link that opens the result screen.

Empty state: "You have not taken a test yet" with a button "Start a quick test".
```

## Prompt 11: Progress and recommendations

```
Design the Progress page.

Top: a black hero card with "Tests taken 12" and "Average score 68%".

Section "Strengths and weak areas": one card per subject, and inside it a row per unit with the unit name, a thin horizontal bar for the average score, the percentage, and a chip: "Strong" (green, tick) at 75% or more, "Needs work" (red, warning icon) below 50%, "Okay" (grey) in between. Units with no attempts show "Not attempted yet".

Section "What to revise next": a list of up to 5 recommendation cards, each with the unit name, the reason ("Average 40% over 2 tests"), and two buttons: "Open notes" and "Practise this unit".

Empty state: "Take a quick test to see your progress here."
```

## Prompt 12: AI study assistant

```
Design the "AI Assistant" page. It is NOT a chat. It has three tabs: "Explain", "Summarize", "Practice questions".

EXPLAIN tab: a Subject dropdown, a text field "Topic" and a "Level" choice (Simple, Detailed). A character counter under the field ("0 / 200"). One blue button "Explain". Near the button: "24 of 30 requests left today".

SUMMARIZE tab: a large text area "Paste your notes" with a counter ("0 / 6000"), and a button "Summarize".

PRACTICE QUESTIONS tab: Subject and Unit dropdowns, a number choice (5 or 10), and a button "Generate questions".

RESULT area under the form, in a white card: at the top a clearly visible grey label "AI-generated. Check it against your course material." and a line "Based on general knowledge" (or "Based on the text you provided"). Then the answer as plain headings, paragraphs and bullet lists.

Also show:
- the waiting state: the button disabled with a spinner and the text "This can take up to 30 seconds";
- the limit state: "You have used all 30 requests for today. They reset at midnight.";
- the error state: "The assistant is unavailable right now. Please try again." with a Retry button.

Above the form, a small privacy note: "Do not paste personal details. Text you send is processed by an AI provider."
```

---

## What to send back

For each screen: one desktop and one phone screenshot. Say which screens you approve and which you want changed. The pages are then built in the order of the implementation plan (attendance first).
