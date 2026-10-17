# 0011. Real student data in the demonstration deployment

- Status: Accepted by the owner on 2026-10-18
- Date: 2026-10-18
- Deciders: Laxman Sirvi
- Changes: security rule SR-42 and agent rule AG-11 gain exception EX-DATA-01

## Context

SR-42 allowed only synthetic data in every environment, and AG-11 forbade an AI agent from handling real student information even when given it. On 2026-10-18 the owner gave the agent the attendance shortage report of one class (B.Tech CSE(AI&ML), II-I semester, section E, 70 students, attendance from 27 July to 30 September 2026) and asked for it to be loaded into the hosted database, so that the demonstration runs on the real class. The owner stated that the site is a demonstration used within limits and not by others, and that the data is attendance and, later, marks.

The owner was told before deciding: the data belongs to the college and to the classmates; anyone who knows a roll number can log in as that student until the student changes the first password (ADR 0010); and a photo transcription can misread a digit.

## Decision

Exception **EX-DATA-01**: the owner's own class may be stored in the **hosted demonstration database only**, with these conditions.

1. **Only what the application needs:** roll number, name, branch, semester, section, admission year, and the attendance totals. Gender, residence type and signatures from the sheet are not stored.
2. **Never in Git.** The working files are kept in `private-data/`, which is ignored by Git. No real name or roll number goes into code, tests, documentation, examples or commit messages. Tests keep using synthetic data.
3. **Checked before loading.** Every attendance row is checked by arithmetic (attended × 100 ÷ held must equal the printed percentage), and roll numbers whose characters are hard to read are confirmed by the owner before those students are created, because a roll number cannot be changed later.
4. **ADR 0010 applies:** the first password is the roll number in capital letters and must be changed at the first login.
5. **Removal:** when the demonstration is over, the owner deletes the students and their records, or the Supabase project.

## Consequences

- The development database and the test suite stay synthetic.
- The owner, not the agent, is responsible for having the right to use the data.
