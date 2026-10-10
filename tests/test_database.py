import unittest

from sms import Student, StudentDatabase, StudentError


class StudentDatabaseTest(unittest.TestCase):
    def setUp(self):
        self.db = StudentDatabase(":memory:")
        self.db.add(Student("101", "Asha Verma", "BCA", 1, "asha@example.com", 91))
        self.db.add(Student("102", "Rohan Mehta", "BSc", 2, "rohan@example.com", 67))

    def tearDown(self):
        self.db.close()

    def test_add_and_get(self):
        student = self.db.get("101")
        self.assertEqual(student.name, "Asha Verma")
        self.assertEqual(student.grade, "A+")

    def test_duplicate_roll_number_is_rejected(self):
        with self.assertRaises(StudentError):
            self.db.add(Student("101", "Someone Else", "BCA", 1))

    def test_invalid_marks_are_rejected(self):
        with self.assertRaises(StudentError):
            self.db.add(Student("103", "Bad Marks", "BCA", 1, marks=150))

    def test_list_filters_by_course(self):
        self.assertEqual(len(self.db.list_all()), 2)
        self.assertEqual([s.roll_no for s in self.db.list_all("bca")], ["101"])

    def test_search(self):
        self.assertEqual([s.roll_no for s in self.db.search("rohan")], ["102"])
        self.assertEqual(self.db.search("nobody"), [])

    def test_update(self):
        student = self.db.update("102", marks=85, year=3)
        self.assertEqual(student.marks, 85)
        self.assertEqual(student.year, 3)
        self.assertEqual(student.grade, "A")

    def test_update_unknown_student(self):
        with self.assertRaises(StudentError):
            self.db.update("999", name="Ghost")

    def test_delete(self):
        self.db.delete("101")
        with self.assertRaises(StudentError):
            self.db.get("101")

    def test_stats(self):
        stats = self.db.stats()
        self.assertEqual(stats["total"], 2)
        self.assertEqual(stats["average"], 79)
        self.assertEqual(stats["topper"].roll_no, "101")
        self.assertEqual(stats["by_course"], {"BCA": 1, "BSc": 1})


if __name__ == "__main__":
    unittest.main()
