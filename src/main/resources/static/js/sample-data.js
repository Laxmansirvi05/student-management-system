// SAMPLE DATA for the page designs. Every name, number and date here is invented.
//
// The student pages were designed before their server features were built. Until a page is
// connected to its API, it reads from this file and shows a "Sample data" label in the top bar.
// Each export has the shape the matching API response will have (docs/09-api-contract.md), so
// connecting a page means replacing one import with one api.get call.
// No real person's data may ever be added here.

/** An ISO date a number of days from today, so the samples never look out of date. */
function day(offset) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** The last class days before today, newest first, skipping Sundays. */
function classHistory(count, absentAt) {
  const rows = [];
  let offset = 0;
  while (rows.length < count) {
    offset -= 1;
    const date = new Date();
    date.setDate(date.getDate() + offset);
    if (date.getDay() === 0) {
      continue;
    }
    rows.push({
      date: day(offset),
      periodNo: (rows.length % 3) + 1,
      status: absentAt.includes(rows.length) ? "ABSENT" : "PRESENT",
    });
  }
  return rows;
}

export const subjects = [
  { code: "CS301", name: "Data Structures", credits: 4, faculty: "Faculty A" },
  { code: "MA301", name: "Discrete Mathematics", credits: 4, faculty: "Faculty B" },
  { code: "EC301", name: "Digital Logic Design", credits: 3, faculty: "Faculty C" },
  { code: "CS302", name: "Object Oriented Programming", credits: 4, faculty: "Faculty D" },
  { code: "ES301", name: "Environmental Science", credits: 2, faculty: "Faculty E" },
];

const units = {
  CS301: ["Arrays and Complexity", "Stacks, Queues and Linked Lists", "Trees", "Graphs", "Hashing"],
  MA301: ["Logic and Sets", "Relations and Functions", "Combinatorics", "Graph Theory"],
  EC301: ["Boolean Algebra", "Combinational Circuits", "Sequential Circuits", "Registers and Counters"],
  CS302: ["Classes and Objects", "Inheritance", "Polymorphism", "Exceptions and Collections"],
};

export const attendance = {
  requiredPercent: 75,
  overall: { classesHeld: 144, classesAttended: 116, percentage: 80.56 },
  subjects: [
    { code: "CS301", name: "Data Structures", classesHeld: 40, classesAttended: 33, percentage: 82.5, belowRequired: false, history: classHistory(12, [2, 6]) },
    { code: "MA301", name: "Discrete Mathematics", classesHeld: 38, classesAttended: 32, percentage: 84.21, belowRequired: false, history: classHistory(12, [4]) },
    { code: "EC301", name: "Digital Logic Design", classesHeld: 30, classesAttended: 21, percentage: 70.0, belowRequired: true, history: classHistory(12, [0, 3, 5, 9]) },
    { code: "CS302", name: "Object Oriented Programming", classesHeld: 36, classesAttended: 30, percentage: 83.33, belowRequired: false, history: classHistory(12, [1, 8]) },
    { code: "ES301", name: "Environmental Science", classesHeld: 0, classesAttended: 0, percentage: null, belowRequired: false, history: [] },
  ],
};

export const marks = {
  semesters: [3, 2, 1],
  currentSemester: 3,
  subjects: [
    {
      code: "CS301", name: "Data Structures", credits: 4, published: true,
      assessments: [
        { name: "Internal 1", type: "INTERNAL", marksObtained: 44, maxMarks: 50, absent: false },
        { name: "Assignment 1", type: "ASSIGNMENT", marksObtained: 19, maxMarks: 20, absent: false },
        { name: "Lab 1", type: "LAB", marksObtained: 28.5, maxMarks: 30, absent: false },
      ],
    },
    {
      code: "MA301", name: "Discrete Mathematics", credits: 4, published: true,
      assessments: [
        { name: "Internal 1", type: "INTERNAL", marksObtained: 38, maxMarks: 50, absent: false },
        { name: "Assignment 1", type: "ASSIGNMENT", marksObtained: 17.5, maxMarks: 20, absent: false },
      ],
    },
    {
      code: "EC301", name: "Digital Logic Design", credits: 3, published: true,
      assessments: [
        { name: "Internal 1", type: "INTERNAL", marksObtained: 31, maxMarks: 50, absent: false },
        { name: "Lab 1", type: "LAB", marksObtained: 22, maxMarks: 30, absent: false },
        { name: "Assignment 1", type: "ASSIGNMENT", marksObtained: null, maxMarks: 20, absent: true },
      ],
    },
    {
      code: "CS302", name: "Object Oriented Programming", credits: 4, published: true,
      assessments: [
        { name: "Internal 1", type: "INTERNAL", marksObtained: 42.5, maxMarks: 50, absent: false },
        { name: "Assignment 1", type: "ASSIGNMENT", marksObtained: 20, maxMarks: 20, absent: false },
        { name: "Lab 1", type: "LAB", marksObtained: 27, maxMarks: 30, absent: false },
      ],
    },
    { code: "ES301", name: "Environmental Science", credits: 2, published: false, assessments: [] },
  ],
};

export const results = {
  cgpa: 7.12,
  semesters: [
    { semester: 2, sgpa: 7.8, creditsRegistered: 22, creditsEarned: 22, resultStatus: "PASS", publishedAt: day(-95) },
    { semester: 1, sgpa: 6.5, creditsRegistered: 24, creditsEarned: 20, resultStatus: "FAIL", publishedAt: day(-270) },
  ],
};

// One row per class: [day of week (1 = Monday), start, end, subject code, room].
const classes = [
  [1, "09:00", "10:00", "EC301", "Room 204"], [1, "10:00", "11:00", "MA301", "Room 204"], [1, "11:15", "12:15", "CS302", "Room 201"], [1, "14:00", "16:00", "CS301", "Lab 1"],
  [2, "09:00", "10:00", "CS301", "Room 204"], [2, "10:00", "11:00", "EC301", "Room 204"], [2, "11:15", "12:15", "MA301", "Room 108"], [2, "14:00", "15:00", "ES301", "Room 110"],
  [3, "09:00", "10:00", "MA301", "Room 204"], [3, "10:00", "11:00", "CS301", "Room 204"], [3, "11:15", "12:15", "CS302", "Room 201"], [3, "14:00", "16:00", "EC301", "Lab 3"],
  [4, "09:00", "10:00", "CS302", "Room 201"], [4, "10:00", "11:00", "MA301", "Room 204"], [4, "11:15", "12:15", "CS301", "Room 204"], [4, "14:00", "15:00", "EC301", "Room 204"],
  [5, "09:00", "10:00", "EC301", "Room 204"], [5, "10:00", "11:00", "CS302", "Room 201"], [5, "11:15", "12:15", "MA301", "Room 204"], [5, "14:00", "16:00", "CS302", "Lab 2"],
  [6, "09:00", "10:00", "CS301", "Room 204"], [6, "10:00", "11:00", "ES301", "Room 110"],
];

export const timetable = classes.map(([dayOfWeek, startTime, endTime, code, room]) => {
  const subject = subjects.find((entry) => entry.code === code);
  return { dayOfWeek, startTime, endTime, subjectCode: code, subjectName: subject.name, room, facultyName: subject.faculty };
});

export const notices = [
  { id: 1, category: "EXAM", title: "Mid-term examination schedule", publishedAt: day(-1), dueDate: day(2),
    body: "The mid-term examinations for Semester 3 begin next week.\n\nCheck your timetable for the room of each paper. Reach the hall ten minutes before the start and carry your college identity card. Calculators are allowed only where the question paper says so.\n\nIf a paper clashes with another one, tell the examination office before the due date." },
  { id: 2, category: "ASSIGNMENT", title: "Data Structures: Assignment 2 on linked lists", publishedAt: day(-2), dueDate: day(6),
    body: "Implement a singly linked list with insert, delete, search and reverse operations, and write a short note on the time taken by each.\n\nSubmit one PDF. Late submissions lose marks." },
  { id: 3, category: "GENERAL", title: "Library books due for renewal", publishedAt: day(-4), dueDate: day(-1),
    body: "Books issued before the start of this month are due for renewal. Renew them at the library desk to avoid a fine." },
  { id: 4, category: "EVENT", title: "Coding club: weekend problem-solving session", publishedAt: day(-5), dueDate: day(12),
    body: "The coding club is holding a problem-solving session this month. Teams of two or three. Register with your class representative before the due date." },
  { id: 5, category: "ASSIGNMENT", title: "Digital Logic Design: lab record submission", publishedAt: day(-6), dueDate: day(9),
    body: "Complete experiments 1 to 6 in your lab record and submit it to the lab assistant." },
  { id: 6, category: "GENERAL", title: "Network maintenance in the main block", publishedAt: day(-8), dueDate: null,
    body: "The campus network in the main block will be under maintenance on Saturday afternoon. Lab computers may be offline during that time." },
  { id: 7, category: "EXAM", title: "Internal 1 marks published", publishedAt: day(-10), dueDate: null,
    body: "Marks for Internal 1 have been published for most subjects. Check the Results page. If you think a mark is wrong, speak to the subject faculty within a week." },
  { id: 8, category: "ASSIGNMENT", title: "Discrete Mathematics: problem set 3", publishedAt: day(-12), dueDate: day(15),
    body: "Solve problems 1 to 12 from the combinatorics chapter and submit your working." },
];

const resourceRows = [
  [1, "Stacks, queues and linked lists: complete notes", "CS301", 2, "NOTES", 2516582, -9, true],
  [2, "Discrete Mathematics model paper with solutions", "MA301", null, "MODEL_PAPER", 1887436, -16, false],
  [3, "Digital Logic Design lab manual, experiments 1 to 8", "EC301", null, "LAB_MANUAL", 5033164, -1, true],
  [4, "Propositional logic and set theory lecture notes", "MA301", 1, "NOTES", 1153433, -28, false],
  [5, "Object Oriented Programming: previous year question paper", "CS302", null, "QUESTION_PAPER", 6501171, -3, false],
  [6, "Karnaugh maps and sequential circuits: important questions", "EC301", 3, "IMPORTANT_QUESTIONS", 3145728, -12, true],
  [7, "Trees and tree traversals: complete notes", "CS301", 3, "NOTES", 2936012, -6, false],
  [8, "Data Structures syllabus, Semester 3", "CS301", null, "SYLLABUS", 419430, -60, false],
  [9, "Inheritance and polymorphism: worked examples", "CS302", 2, "NOTES", 1677721, -14, false],
  [10, "Combinatorics: important questions", "MA301", 3, "IMPORTANT_QUESTIONS", 943718, -7, false],
  [11, "Data Structures: previous year question paper", "CS301", null, "QUESTION_PAPER", 2202009, -20, false],
  [12, "Boolean algebra quick revision notes", "EC301", 1, "NOTES", 838860, -33, false],
  [13, "Object Oriented Programming lab manual", "CS302", null, "LAB_MANUAL", 4404019, -25, false],
  [14, "Graph theory lecture notes", "MA301", 4, "NOTES", 2097152, -4, false],
];

export const resources = resourceRows.map(([id, title, code, unitNo, resourceType, fileSizeBytes, offset, bookmarked]) => ({
  id, title, subjectCode: code, subjectName: subjects.find((entry) => entry.code === code).name,
  unitNo, unitTitle: unitNo ? units[code][unitNo - 1] : null, resourceType, fileSizeBytes,
  originalFileName: title.toLowerCase().replace(/[^a-z0-9]+/g, "-") + ".pdf", createdAt: day(offset), bookmarked,
}));

export const resourceTypes = {
  NOTES: { label: "Notes", kind: "primary" },
  SYLLABUS: { label: "Syllabus", kind: "teal" },
  LAB_MANUAL: { label: "Lab manual", kind: "purple" },
  QUESTION_PAPER: { label: "Question paper", kind: "danger" },
  MODEL_PAPER: { label: "Model paper", kind: "orange" },
  IMPORTANT_QUESTIONS: { label: "Important questions", kind: "success" },
};

export const quiz = {
  subjects: subjects.filter((subject) => units[subject.code]).map((subject) => ({
    code: subject.code, name: subject.name,
    units: units[subject.code].map((title, index) => ({ unitNo: index + 1, title, questionsAvailable: [24, 38, 31, 12, 4][index] })),
  })),
  // In the real feature the correct answers never reach the browser before the test is submitted;
  // the server marks the test. They are here only so the design can show a result.
  questions: [
    { text: "Which data structure works on the first in, first out principle?", options: ["Stack", "Queue", "Binary tree", "Hash table"], correct: 1, explanation: "A queue removes items in the order they were added: the first one in is the first one out." },
    { text: "Which operation removes the top element of a stack?", options: ["Push", "Peek", "Pop", "Enqueue"], correct: 2, explanation: "Pop removes and returns the top element. Peek only looks at it, and push adds one." },
    { text: "Which data structure does breadth-first search use to remember the vertices to visit next?", options: ["Stack", "Queue", "Priority queue", "Binary search tree"], correct: 1, explanation: "Breadth-first search visits vertices level by level, so it needs a queue: vertices found first are explored first." },
    { text: "How long does it take to insert a node at the head of a singly linked list?", options: ["O(1)", "O(log n)", "O(n)", "O(n log n)"], correct: 0, explanation: "Only the new node's pointer and the head pointer change, whatever the length of the list." },
    { text: "Which data structure is used to evaluate a postfix expression?", options: ["Queue", "Stack", "Linked list", "Graph"], correct: 1, explanation: "Operands are pushed on a stack. Each operator pops its operands and pushes the result back." },
    { text: "In a circular queue stored in an array of size n, when is the queue full?", options: ["front == rear", "rear == n - 1", "(rear + 1) % n == front", "front == 0"], correct: 2, explanation: "One slot is kept empty, so the queue is full when the slot after rear is front." },
    { text: "Which structure allows insertion and deletion at both ends?", options: ["Stack", "Queue", "Deque", "Heap"], correct: 2, explanation: "A deque, or double-ended queue, allows adding and removing at the front and at the back." },
    { text: "Reversing a singly linked list in place takes how much time and extra space?", options: ["O(n) time, O(1) space", "O(n) time, O(n) space", "O(n²) time, O(1) space", "O(log n) time, O(1) space"], correct: 0, explanation: "One pass re-points every node at the node before it, using three pointers and no extra list." },
    { text: "Which of these is not a typical use of a stack?", options: ["Function calls", "Undo in an editor", "Breadth-first search", "Checking balanced brackets"], correct: 2, explanation: "Breadth-first search needs a queue. The other three all rely on last in, first out." },
    { text: "How long does it take to reach the k-th node of a singly linked list?", options: ["O(1)", "O(log k)", "O(k)", "O(k²)"], correct: 2, explanation: "A linked list has no index. You must follow k pointers from the head." },
  ],
  // The answers chosen in a past attempt, used to show the result screen. -1 means not answered.
  sampleAnswers: [1, 2, 1, 0, 1, 1, 2, 0, -1, 0],
  attempts: [
    { id: 12, takenAt: day(-1), subjectName: "Data Structures", unitNo: 2, unitTitle: "Stacks, Queues and Linked Lists", score: 7, total: 10 },
    { id: 11, takenAt: day(-3), subjectName: "Discrete Mathematics", unitNo: 1, unitTitle: "Logic and Sets", score: 13, total: 15 },
    { id: 10, takenAt: day(-6), subjectName: "Object Oriented Programming", unitNo: 3, unitTitle: "Polymorphism", score: 7, total: 10 },
    { id: 9, takenAt: day(-9), subjectName: "Digital Logic Design", unitNo: 3, unitTitle: "Sequential Circuits", score: 4, total: 10 },
    { id: 8, takenAt: day(-12), subjectName: "Data Structures", unitNo: 1, unitTitle: "Arrays and Complexity", score: 9, total: 10 },
    { id: 7, takenAt: day(-15), subjectName: "Data Structures", unitNo: 2, unitTitle: "Stacks, Queues and Linked Lists", score: 4, total: 10 },
    { id: 6, takenAt: day(-18), subjectName: "Discrete Mathematics", unitNo: 3, unitTitle: "Combinatorics", score: 6, total: 10 },
    { id: 5, takenAt: day(-22), subjectName: "Object Oriented Programming", unitNo: 1, unitTitle: "Classes and Objects", score: 8, total: 10 },
  ],
};

function unitProgress(code, averages) {
  return units[code].map((title, index) => ({
    unitNo: index + 1, title, averagePercent: averages[index] ?? null, attempts: averages[index] == null ? 0 : 2,
  }));
}

export const progress = {
  testsTaken: 8,
  averagePercent: 67,
  subjects: [
    { code: "CS301", name: "Data Structures", averagePercent: 62, units: unitProgress("CS301", [84, 40, 62]) },
    { code: "MA301", name: "Discrete Mathematics", averagePercent: 74, units: unitProgress("MA301", [87, 76, 60]) },
    { code: "EC301", name: "Digital Logic Design", averagePercent: 71, units: unitProgress("EC301", [92, 76, 46]) },
    { code: "CS302", name: "Object Oriented Programming", averagePercent: 75, units: unitProgress("CS302", [80, null, 70]) },
  ],
  recommendations: [
    { subjectCode: "CS301", subjectName: "Data Structures", unitNo: 2, unitTitle: "Stacks, Queues and Linked Lists", averagePercent: 40, attempts: 2, resourceId: 1 },
    { subjectCode: "EC301", subjectName: "Digital Logic Design", unitNo: 3, unitTitle: "Sequential Circuits", averagePercent: 46, attempts: 2, resourceId: 6 },
    { subjectCode: "MA301", subjectName: "Discrete Mathematics", unitNo: 3, unitTitle: "Combinatorics", averagePercent: 60, attempts: 2, resourceId: 10 },
    { subjectCode: "CS301", subjectName: "Data Structures", unitNo: 3, unitTitle: "Trees", averagePercent: 62, attempts: 2, resourceId: 7 },
  ],
};

export const assistant = {
  dailyLimit: 30,
  remainingToday: 24,
  explain: {
    title: "Stacks and queues",
    basis: "Based on general knowledge",
    sections: [
      { heading: "The idea in one line", paragraphs: ["A stack and a queue both hold items in order. They differ only in which item leaves first."] },
      { heading: "Stack: last in, first out", paragraphs: ["Think of a pile of plates. You add a plate on top and you take a plate from the top."], bullets: ["Push adds an item on top.", "Pop removes the item on top.", "Peek looks at the top item without removing it."] },
      { heading: "Queue: first in, first out", paragraphs: ["Think of a line at a counter. People join at the back and are served from the front."], bullets: ["Enqueue adds an item at the back.", "Dequeue removes the item at the front."] },
      { heading: "Where each is used", bullets: ["Stack: function calls, undo, checking brackets, depth-first search.", "Queue: scheduling, printing jobs, breadth-first search."] },
    ],
  },
  summary: {
    title: "Summary of your notes",
    basis: "Based on the text you provided",
    sections: [
      { heading: "Key points", bullets: ["A linked list stores each item in a node that points to the next node.", "Inserting or deleting at the head takes constant time.", "Finding the k-th item means walking k nodes from the head.", "A doubly linked list also points back, which makes deleting a known node simple."] },
      { heading: "Remember for the exam", bullets: ["Arrays give fast access by position; linked lists give fast insertion.", "Always update the pointers in the right order, or part of the list is lost."] },
    ],
  },
  questions: {
    title: "Practice questions",
    basis: "Based on general knowledge",
    sections: [
      { heading: "Try these", numbered: ["Explain the difference between a stack and a queue with one example of each.", "Write the steps to reverse a singly linked list in place.", "When is a circular queue full? Give the condition.", "Why does breadth-first search use a queue and not a stack?", "Convert the infix expression (a + b) * c to postfix and evaluate it with a stack."] },
    ],
  },
};
