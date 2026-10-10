# Student Management System

A simple command-line Student Management System written in Python. It stores student records in a local SQLite database and lets you add, view, search, update and delete students, and see class statistics.

It uses only the Python standard library, so there is nothing to install.

## Features

- Add a student with roll number, name, course, year, email and marks
- List all students, or only the students of one course
- Search by name, roll number or course
- Update any detail of a student
- Delete a student
- Automatic grade from marks (A+, A, B, C, D, F)
- Class statistics: total students, average marks, topper and students per course
- Input validation (unique roll numbers, marks 0-100, year 1-6)

## Requirements

- Python 3.9 or newer

## Getting started

```bash
git clone https://github.com/Laxmansirvi05/student-management-system.git
cd student-management-system
python3 main.py --help
```

## Usage

Add students:

```bash
python3 main.py add 101 "Asha Verma" BCA 1 --email asha@example.com --marks 91
python3 main.py add 102 "Rohan Mehta" BSc 2 --marks 67
```

List and search:

```bash
python3 main.py list
python3 main.py list --course BCA
python3 main.py search rohan
python3 main.py show 101
```

Update and delete:

```bash
python3 main.py update 102 --marks 85 --year 3
python3 main.py delete 102
```

Statistics:

```bash
python3 main.py stats
```

Example output of `list`:

```
Roll No    Name                   Course       Year  Marks  Grade Email
-----------------------------------------------------------------------
101        Asha Verma             BCA          1     91.0   A+    asha@example.com
102        Rohan Mehta            BSc          2     67.0   C

2 student(s)
```

Records are saved in `students.db` in the project folder. Use `--db` to keep the data somewhere else:

```bash
python3 main.py --db /path/to/school.db list
```

## Grading scale

| Marks    | Grade |
| -------- | ----- |
| 90 - 100 | A+    |
| 80 - 89  | A     |
| 70 - 79  | B     |
| 60 - 69  | C     |
| 40 - 59  | D     |
| Below 40 | F     |

## Project structure

```
.
├── main.py              # command-line interface
├── sms/
│   ├── __init__.py
│   └── database.py      # Student model and SQLite storage
└── tests/
    └── test_database.py # unit tests
```

## Running the tests

```bash
python3 -m unittest discover -s tests -v
```

## Possible future improvements

- Attendance tracking
- Subject-wise marks and report cards
- Export to CSV
- A web or desktop interface
