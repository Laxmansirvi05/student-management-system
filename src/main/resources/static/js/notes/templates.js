// Starting points, not note types. A template only pre-fills the title and body of an ordinary
// note: there is no template column and nothing downstream can tell a templated note from a
// hand-written one, so search, folders and sharing never have to care.

const TASK = (text) =>
  `<li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p>${text}</p></div></li>`;

export const NOTE_TEMPLATES = [
  { id: "blank", name: "Blank note", icon: "edit_note", description: "Start from nothing", title: "", content: "" },
  {
    id: "interview-prep",
    name: "Interview prep",
    icon: "record_voice_over",
    description: "Questions, answers, follow-ups",
    title: "Interview prep",
    content:
      "<h2>Role &amp; company</h2><p></p><h2>Questions I expect</h2>" +
      `<ul data-type="taskList">${TASK("Tell me about yourself")}${TASK("Walk me through a project")}</ul>` +
      "<h2>My answers</h2><p></p><h2>Questions to ask them</h2><p></p>",
  },
  {
    id: "company-research",
    name: "Company research",
    icon: "domain",
    description: "Role, requirements, deadline",
    title: "Company research",
    content:
      "<h2>Company</h2><p></p><h2>Role</h2><p></p><h2>Requirements</h2><ul><li><p></p></li></ul>" +
      "<h2>Deadline</h2><p></p><h2>Notes</h2><p></p>",
  },
  {
    id: "application-prep",
    name: "Application prep",
    icon: "assignment_turned_in",
    description: "Resume changes, skills, topics",
    title: "Application prep",
    content:
      `<h2>Resume changes</h2><ul data-type="taskList">${TASK("")}</ul>` +
      "<h2>Skills to revise</h2><ul><li><p></p></li></ul><h2>Interview topics</h2><ul><li><p></p></li></ul>",
  },
  {
    id: "project",
    name: "Project notes",
    icon: "rocket_launch",
    description: "Ideas, architecture, TODOs",
    title: "Project notes",
    content: `<h2>Idea</h2><p></p><h2>Architecture</h2><p></p><h2>TODO</h2><ul data-type="taskList">${TASK("")}</ul><h2>Links</h2><p></p>`,
  },
  {
    id: "study",
    name: "Study notes",
    icon: "menu_book",
    description: "Topic, key points, revision",
    title: "Study notes",
    content: `<h2>Topic</h2><p></p><h2>Key points</h2><ul><li><p></p></li></ul><h2>To revise</h2><ul data-type="taskList">${TASK("")}</ul>`,
  },
  {
    id: "meeting",
    name: "Meeting notes",
    icon: "groups",
    description: "Attendees, decisions, actions",
    title: "Meeting notes",
    content:
      "<h2>Attendees</h2><p></p><h2>Discussed</h2><ul><li><p></p></li></ul><h2>Decisions</h2><p></p>" +
      `<h2>Action items</h2><ul data-type="taskList">${TASK("")}</ul>`,
  },
];
