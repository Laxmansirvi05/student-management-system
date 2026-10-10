"""SQLite storage layer for the Student Management System."""

import sqlite3
from dataclasses import dataclass
from pathlib import Path

DEFAULT_DB_PATH = Path(__file__).resolve().parent.parent / "students.db"

UPDATABLE_FIELDS = ("name", "course", "year", "email", "marks")


class StudentError(Exception):
    """Raised when a student operation cannot be completed."""


@dataclass
class Student:
    roll_no: str
    name: str
    course: str
    year: int
    email: str = ""
    marks: float = 0.0

    @property
    def grade(self):
        if self.marks >= 90:
            return "A+"
        if self.marks >= 80:
            return "A"
        if self.marks >= 70:
            return "B"
        if self.marks >= 60:
            return "C"
        if self.marks >= 40:
            return "D"
        return "F"


def _validate(year, marks):
    if year is not None and not 1 <= year <= 6:
        raise StudentError("Year must be between 1 and 6.")
    if marks is not None and not 0 <= marks <= 100:
        raise StudentError("Marks must be between 0 and 100.")


class StudentDatabase:
    def __init__(self, db_path=DEFAULT_DB_PATH):
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute(
            """
            CREATE TABLE IF NOT EXISTS students (
                roll_no TEXT PRIMARY KEY,
                name    TEXT NOT NULL,
                course  TEXT NOT NULL,
                year    INTEGER NOT NULL,
                email   TEXT NOT NULL DEFAULT '',
                marks   REAL NOT NULL DEFAULT 0
            )
            """
        )
        self.conn.commit()

    def close(self):
        self.conn.close()

    def add(self, student):
        if not student.roll_no.strip() or not student.name.strip():
            raise StudentError("Roll number and name are required.")
        _validate(student.year, student.marks)
        try:
            self.conn.execute(
                "INSERT INTO students (roll_no, name, course, year, email, marks) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (
                    student.roll_no,
                    student.name,
                    student.course,
                    student.year,
                    student.email,
                    student.marks,
                ),
            )
        except sqlite3.IntegrityError:
            raise StudentError(
                f"A student with roll number {student.roll_no} already exists."
            ) from None
        self.conn.commit()

    def get(self, roll_no):
        row = self.conn.execute(
            "SELECT * FROM students WHERE roll_no = ?", (roll_no,)
        ).fetchone()
        if row is None:
            raise StudentError(f"No student found with roll number {roll_no}.")
        return Student(**row)

    def list_all(self, course=None):
        if course:
            rows = self.conn.execute(
                "SELECT * FROM students WHERE course = ? COLLATE NOCASE ORDER BY roll_no",
                (course,),
            )
        else:
            rows = self.conn.execute("SELECT * FROM students ORDER BY roll_no")
        return [Student(**row) for row in rows]

    def search(self, keyword):
        pattern = f"%{keyword}%"
        rows = self.conn.execute(
            "SELECT * FROM students "
            "WHERE name LIKE ? OR roll_no LIKE ? OR course LIKE ? "
            "ORDER BY roll_no",
            (pattern, pattern, pattern),
        )
        return [Student(**row) for row in rows]

    def update(self, roll_no, **changes):
        changes = {
            field: value
            for field, value in changes.items()
            if field in UPDATABLE_FIELDS and value is not None
        }
        if not changes:
            raise StudentError("Nothing to update.")
        _validate(changes.get("year"), changes.get("marks"))
        self.get(roll_no)
        assignments = ", ".join(f"{field} = ?" for field in changes)
        self.conn.execute(
            f"UPDATE students SET {assignments} WHERE roll_no = ?",
            (*changes.values(), roll_no),
        )
        self.conn.commit()
        return self.get(roll_no)

    def delete(self, roll_no):
        self.get(roll_no)
        self.conn.execute("DELETE FROM students WHERE roll_no = ?", (roll_no,))
        self.conn.commit()

    def stats(self):
        students = self.list_all()
        if not students:
            return {"total": 0, "average": 0.0, "topper": None, "by_course": {}}
        by_course = {}
        for student in students:
            by_course[student.course] = by_course.get(student.course, 0) + 1
        return {
            "total": len(students),
            "average": sum(s.marks for s in students) / len(students),
            "topper": max(students, key=lambda s: s.marks),
            "by_course": by_course,
        }
