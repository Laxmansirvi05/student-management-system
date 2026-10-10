"""Command-line interface for the Student Management System."""

import argparse
import sys

from sms import Student, StudentDatabase, StudentError
from sms.database import DEFAULT_DB_PATH


def print_table(students):
    if not students:
        print("No students found.")
        return
    header = f"{'Roll No':<10} {'Name':<22} {'Course':<12} {'Year':<5} {'Marks':<6} {'Grade':<5} Email"
    print(header)
    print("-" * len(header))
    for s in students:
        print(
            f"{s.roll_no:<10} {s.name:<22} {s.course:<12} {s.year:<5} "
            f"{s.marks:<6.1f} {s.grade:<5} {s.email}"
        )
    print(f"\n{len(students)} student(s)")


def build_parser():
    parser = argparse.ArgumentParser(
        prog="sms", description="Student Management System"
    )
    parser.add_argument(
        "--db", default=DEFAULT_DB_PATH, help="path to the SQLite database file"
    )
    commands = parser.add_subparsers(dest="command", required=True)

    add = commands.add_parser("add", help="add a new student")
    add.add_argument("roll_no")
    add.add_argument("name")
    add.add_argument("course")
    add.add_argument("year", type=int)
    add.add_argument("--email", default="")
    add.add_argument("--marks", type=float, default=0.0)

    list_cmd = commands.add_parser("list", help="list all students")
    list_cmd.add_argument("--course", help="only show students of this course")

    search = commands.add_parser("search", help="search by name, roll number or course")
    search.add_argument("keyword")

    show = commands.add_parser("show", help="show one student")
    show.add_argument("roll_no")

    update = commands.add_parser("update", help="update a student's details")
    update.add_argument("roll_no")
    update.add_argument("--name")
    update.add_argument("--course")
    update.add_argument("--year", type=int)
    update.add_argument("--email")
    update.add_argument("--marks", type=float)

    delete = commands.add_parser("delete", help="delete a student")
    delete.add_argument("roll_no")

    commands.add_parser("stats", help="show class statistics")
    return parser


def run(args, db):
    if args.command == "add":
        db.add(
            Student(
                args.roll_no, args.name, args.course, args.year, args.email, args.marks
            )
        )
        print(f"Added {args.name} ({args.roll_no}).")
    elif args.command == "list":
        print_table(db.list_all(args.course))
    elif args.command == "search":
        print_table(db.search(args.keyword))
    elif args.command == "show":
        print_table([db.get(args.roll_no)])
    elif args.command == "update":
        student = db.update(
            args.roll_no,
            name=args.name,
            course=args.course,
            year=args.year,
            email=args.email,
            marks=args.marks,
        )
        print(f"Updated {student.name} ({student.roll_no}).")
    elif args.command == "delete":
        db.delete(args.roll_no)
        print(f"Deleted student {args.roll_no}.")
    elif args.command == "stats":
        stats = db.stats()
        print(f"Total students : {stats['total']}")
        print(f"Average marks  : {stats['average']:.2f}")
        if stats["topper"]:
            topper = stats["topper"]
            print(f"Topper         : {topper.name} ({topper.roll_no}) - {topper.marks:.1f}")
        for course, count in sorted(stats["by_course"].items()):
            print(f"  {course}: {count}")


def main(argv=None):
    args = build_parser().parse_args(argv)
    db = StudentDatabase(args.db)
    try:
        run(args, db)
    except StudentError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    finally:
        db.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
