# 22. Loading real data

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-17

The steps for putting the college's real students and records into the deployed application. It follows [ADR 0010](decisions/0010-roll-number-first-password-with-real-students.md). **Real data is never given to an AI assistant, never put in this repository, and never loaded into the demonstration database.** The owner loads it through the admin screens.

## 1. Before the first real student (owner tasks)

| # | Task | Where |
| --- | --- | --- |
| 1 | Deploy with `SPRING_PROFILES_ACTIVE=prod` and `DEMO_SEED_ENABLED` not set, on Render or Heroku | [23-deploying-on-render.md](23-deploying-on-render.md), [18-production-plan.md](18-production-plan.md) |
| 2 | Use a database for real data only (not the one that holds demo students) | [18-production-plan.md](18-production-plan.md) |
| 3 | Create the private storage bucket and set `SUPABASE_URL` and the service key, so photos and PDFs work | T3-01 |
| 4 | Set `STUDENT_EMAIL_DOMAIN=gcet.edu.in` on Heroku (OQ-15 is answered: the email is the roll number in lower case at that domain) | Heroku config |
| 5 | Create the first admin with `ADMIN_BOOTSTRAP_EMAIL` and a strong password, log in, change the password, then remove the bootstrap variables. **Done on 2026-10-22 for the hosted database:** exactly one administrator exists, on the owner's chosen email, and it still has to change its password at first login | [18-production-plan.md](18-production-plan.md) |
| 6 | Rotate the development passwords and keys that were exposed (finding F4); turn on GitHub secret scanning and push protection (F6, F7) | [21-security-review.md](21-security-review.md) |
| 7 | Have the college read and adopt the privacy policy and terms pages; they currently say they are a demonstration | `privacy.html`, `terms.html` |
| 8 | Take a database backup before the first import | Supabase dashboard |

## 2. Load the students

1. Make a CSV in UTF-8 (a spreadsheet's "Save as CSV" works) with the header line:

   ```text
   rollNo,fullName,branch,currentSemester,section,admissionYear,phone
   ```

   `phone` may be left out. Roll numbers use letters and digits only. Up to 100 students per file, so split a larger class into several files.
2. Do this **shortly before students are told to log in** (rule 1 of ADR 0010).
3. Admin, Students, **Import CSV**, choose the file. If any line is wrong nothing is saved; fix the lines listed and choose the file again.
4. Check the list: count, a few names, and that every account says "Awaiting first login".
5. Tell students: log in with the college email (the roll number in lower case at the college domain) and the roll number in capital letters as the password; you will be asked to choose a new password straight away.
6. Delete the CSV file from your computer and from any shared folder or message.

## 3. Load the academic records

There is no bulk import for these, so use the admin screens, in this order:

1. Subjects and their units.
2. Timetable entries.
3. Attendance sheets, one class at a time.
4. Assessments, then marks sheets; publish a sheet when it is ready for students.
5. Semester results, per student.
6. Notices (publish when ready).
7. Study material (PDFs) and, for Quick Exam, questions.

Start with one class end to end, log in as one of its students, and check the pages before loading the rest.

## 4. A few days later

1. Admin, Students: look at accounts still "Awaiting first login". Reset or deactivate any that should not be open.
2. If a student says their password had already been changed, reset the account and read the audit entry (rule 4 of ADR 0010).
3. Check that unused administrator accounts do not exist.

## 5. Notes, notices and past papers (2026-10-22)

Loaded into the hosted database and the private bucket by private one-off scripts that are not in Git (the files and the scripts stay under `private-data/`, and the class folders `notes/` and `notices/` are ignored). Counts only:

- **Past papers:** 1,242 papers, with 1,223 course codes (see [ADR 0012](decisions/0012-previous-year-question-papers.md)).
- **Notes:** 70 PDF files from 68 source files, for 16 subjects. Word files and a saved web page were converted to PDF, scanned PDFs over 10 MB were re-drawn at lower resolution or split into parts, and every file passed the application's own PDF check before upload. Ten subjects of semesters 1 and 2, and 33 units, were added so the notes have a place; attendance, marks and the timetable use only the current semester and are not affected.
- **Notices:** 5 published notices, each with its official document (the scanned circular; three PDFs and two pictures) and a one-line summary: the regular exam time table of the student's branch, the exam fee circular, the Dussehra circular, and the two supplementary time tables (for the AR18, AR20 and AR22 batches, shown to everyone because they concern older batches; the AR25 class is not listed in them).
- **Set aside:** two Word files with equations, which need exporting to PDF from Word. The three PowerPoint (.ppt) files of one subject are left out for now at the owner's request.
- **Checks:** every stored file was fetched through a signed link and matched its stored checksum; the database rows and the bucket files match one to one.
- **Class timetable (2026-10-23):** 28 entries for CSE(AI&ML), semester 3, section E, from the printed sheet (version 01, effective 06-07-2026), with each class's faculty and room. Labs and sessions that span two periods are one entry. Two slots on the sheet have no course (the coding sessions and "Library / Mentoring / Sports"), so they were added as inactive subjects with no credits, which keeps them out of the subject lists. The Friday 1:30 to 3:20 slot is blacked out on the sheet and is left empty.
