# 0017. Coding portfolio and leaderboard from public coding profiles

- Status: Accepted by the owner's request of 2026-10-30
- Date: 2026-10-30
- Deciders: Laxman Sirvi
- Changes: adds the `coding` package, migration `V20`, the Portfolio and Coding leaderboard pages for students and the admin leaderboard (FR-COD-001 to FR-COD-005); the rules are in [docs/02](../02-PRD.md) section 8b

## Context

The owner wants each student to show their coding work, like Codolio does, and wants admins to see how active every student is. Students already practise on outside sites: GitHub, LeetCode, Codeforces, CodeChef, GeeksforGeeks and HackerRank. The application must read their public numbers from those sites, prove that each account belongs to the student who adds it, and rank students fairly.

Only GitHub and Codeforces publish an official API. LeetCode has a public data endpoint that many tools use. CodeChef, GeeksforGeeks and HackerRank have no public API: their numbers come from the public profile page or the endpoint the page itself calls, and can change without notice.

## Options considered

1. **A third-party aggregator (Codolio or similar).** Students would sign up there; we would depend on another company's account system and terms, and admins could not see the data.
2. **Students type their own numbers.** No outside calls, but nothing stops a student from inventing them.
3. **The server reads each linked public profile itself**, after the student proves the account is theirs.

## Decision

Option 3, inside the existing stack. No library is added: the server uses Java's own `HttpClient` and Jackson, which the project already has.

- **Ownership.** A new link starts as *pending* with a random code (`CC-` and eight characters). The student puts the code anywhere in a text field they control on that site (bio, about me, display name, designation or organisation), presses Verify, and may remove it afterwards. Only verified accounts count. One handle can be verified by one student only.
- **Platforms** (owner's choice): GitHub, LeetCode, Codeforces, CodeChef, GeeksforGeeks and HackerRank.
- **Ranking** (owner's choice): total problems solved across the verified accounts. GitHub has no problem count, so it is shown on the portfolio but adds nothing to the total.
- **Visibility** (owner's choice): a student appears on the students' leaderboard only after switching on "Show me on the leaderboard", and then to the whole college. Admins see every student with a linked account, opted in or not.
- **Refresh.** Numbers are read when an account is verified, when the student presses Refresh (at most every 15 minutes per account), and once a night by a scheduled job that goes through the verified accounts one at a time with a pause between calls. Each read keeps one snapshot per account per day, which draws the student's journey.
- **Safety.** A handle must match a strict pattern before any address is built, the host of every call is fixed in the code, calls time out after 10 seconds, and nothing an outside site returns is shown as HTML: only numbers and a short, trimmed rank label are stored.

### Addendum, 2026-10-30: the portfolio views

At the owner's request the portfolio shows views in the style of Codolio: heatmaps and streaks, awards, topics, the question distribution, and a development view of GitHub. The readers keep, per account, a capped JSON document of that data (`V21`, `coding_accounts.details`). New sources, all public and read by the server:

- **GeeksforGeeks**: its profile API has no bio, so a code placed in the bio could never be found. The reader now also reads the public profile page (`geeksforgeeks.org/profile/<handle>`) for the bio and headline, and the practice API for the solved problems by level (with their dates and languages).
- **LeetCode**: the same endpoint gives the submission calendar, solved problems by topic, badges and languages.
- **Codeforces**: submission times and the topics of accepted problems, from the call already made.
- **CodeChef**: the badges and the daily submissions on the page already read.
- **GitHub**: the public contributions calendar page (`github.com/users/<handle>/contributions`), languages and projects from the repositories already read, and three search calls for commits, pull requests and issues, which are left empty when the search refuses.

Badges are drawn by the application (no image from a site is loaded), project addresses are built from the handle and a checked repository name, and descriptions are plain text.

## Trade-offs

- Three of the six sites can change their pages or endpoints at any time. When one does, that platform's numbers stop updating (the old numbers stay, marked with the date of the last good read) until its reader is fixed; the rest keep working.
- Reading pages that have no public API may go against those sites' terms. The owner chose to include them; the calls are few (one account a night, one at a time) and read only public profile data.
- GitHub allows 60 unauthenticated reads an hour per server. An optional `GITHUB_TOKEN` (a token with no scopes) raises that limit; without it, a nightly run with many students may leave some GitHub numbers for the next night.

## Consequences

- New tables `coding_accounts`, `coding_snapshots` and `coding_leaderboard_optins`, with RLS (migration `V20`, [docs/08](../08-database-design.md) section 4.28).
- New endpoints under `/api/v1/coding` (students) and `/api/v1/admin/coding` (admins) ([docs/09](../09-api-contract.md) section 8.24).
- The privacy page names the six sites and says what is read.
