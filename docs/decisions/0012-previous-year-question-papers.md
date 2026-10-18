# 0012. Previous-year question papers (PYQs) as their own library

- Status: Accepted by the owner's request of 2026-10-18
- Date: 2026-10-18
- Deciders: Laxman Sirvi
- Changes: adds FR-LIB-006 (P1); adds the tables `pyq_papers` and `pyq_bookmarks` (migration V10)

## Context

The owner has about 1,500 previous-year question papers (PDF scans) of every branch, kept in folders by year, and asked for them to be organised by branch and subject, de-duplicated, named as `year(Month)-regular|supply|mid-1|mid-2`, and added to the website with search, download and bookmarks.

The existing study-material hub (FR-LIB-001 to 005) can already hold question papers, but each resource belongs to a row of `subjects`, which has a semester and credits and describes the *current* curriculum. Past papers span old regulations (AR16 to AR25), several branches share subjects, and most subjects have no row. Forcing them into `subjects` would also make every one of them appear in the attendance and marks pages of whoever is in that branch and semester.

## Options considered

1. **Add them as resources of type QUESTION_PAPER**, creating a `subjects` row for each subject name. Reuses the hub, but pollutes the subject lists of students and needs a semester and credits that the papers do not have.
2. **A separate library with its own tables**, keyed by branch and subject *name*, reusing the parts that are generic: the PDF checks, file storage, five-minute signed links, bookmark approach and search escaping.
3. **Keep the papers outside the application**, for example as a shared drive. No search, no saves, no control over who sees what.

## Decision

Option 2.

- **Tables.** `pyq_papers` holds branch, subject name, exam year and month, kind (regular, supply, mid), an optional mid number, an optional regulation and course code, a question-set number (for a sitting with more than one set), the storage path, size, checksum, status and a download counter. `pyq_bookmarks` holds a student's saved papers. Both have Row Level Security and the minimum grants.
- **One copy of one paper.** The checksum is unique (the same file cannot be added twice) and so is the sitting (branch, subject, year, month, kind, mid number, regulation, question set). A second question set of the same sitting is added with a set number.
- **Files.** Every PDF goes through `PdfValidator` (real PDF, at most 10 MB, opens within ten seconds, not encrypted) and is stored in the private bucket under `pyq/<BRANCH>/<random id>.pdf`. Nothing of the file name is in the path (SR-28). A student reaches a file only through a signed link that lasts five minutes (SR-30); a hidden paper answers "not found".
- **Adding papers.** An admin adds one paper with its details, or imports a whole organised folder from the browser. The folder format is `BRANCH/Subject name/2025(December)-supply AR20 (2).pdf`, read by `PyqPathParser`, plain Java with its own tests. The importer sends one file per request at under two a second, so it stays inside the limit of 120 requests a minute, and it is safe to run again because files already added are skipped.
- **Students** browse by branch and subject, search by subject, course code or year, filter by year, exam and regulation, sort (newest, oldest, most downloaded, subject), open a paper in the page, download it under a readable name, save it, and see the papers they opened recently (kept in their own browser only). Only a download is counted.
- **Not stored:** nothing about which student opened which paper. Only a total count per paper.

## Consequences

- The papers are the college's published examination papers, not personal data. They are not part of ADR 0011.
- Storage grows by about 100 MB for the current set. The free Supabase plan's storage limit must be checked before importing everything.
- Subjects are matched by name, so a spelling difference makes two folders. The importer's folder names are the source of truth; an admin can correct a paper's subject afterwards.
