import { confirmAction, dataTable, field, hidePageError, input, numberSelect, openForm, pager, rowAction, select, semesterOptions, textarea } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { categoryLabels, categoryTag, loadInto } from "./parts.js";
import "./shell.js";

// Admin: notices (FR-ADM-006). A notice is written as a draft, then published. It can be
// withdrawn to a draft again, edited, or deleted.

const content = document.getElementById("content");
let pageNumber = 0;

function noticeForm(notice) {
  openForm({
    title: notice ? "Edit notice" : "New notice",
    intro: notice ? "" : "It is saved as a draft. Publish it from the list when it is ready.",
    submitLabel: notice ? "Save" : "Save draft",
    fields: [
      h("div.field--wide", field("Title", input("title", { value: notice?.title || "", maxlength: 200 }))),
      h("div.field--wide", field("Text", textarea("body", { maxlength: 5000 }), "Plain text, up to 5,000 characters. Leave an empty line between paragraphs.")),
      field("Category", select("category", Object.entries(categoryLabels), notice?.category || "GENERAL")),
      field("Due date (optional)", input("dueDate", { type: "date", value: notice?.dueDate || "" }), "A due date makes the notice a deadline."),
      field("Branch (optional)", input("targetBranch", { value: notice?.targetBranch || "", maxlength: 20 }), "Leave empty for all branches."),
      field("Semester (optional)", numberSelect("targetSemester", semesterOptions("All semesters"), notice?.targetSemester || "")),
    ],
    wide: true,
    onSubmit: async (values) => {
      if (notice) {
        await api.put("/admin/notices/" + notice.id, values);
      } else {
        await api.post("/admin/notices", values);
      }
      toast("Saved");
      load();
    },
  }).querySelector("textarea").value = notice?.body || "";
}

async function setPublished(notice, publish) {
  await api.post(`/admin/notices/${notice.id}/${publish ? "publish" : "unpublish"}`);
  toast(publish ? "Published" : "Returned to draft");
  load();
}

async function remove(notice) {
  const yes = await confirmAction({ title: "Delete this notice?", text: `"${notice.title}" is removed for everyone. This cannot be undone.`, confirmLabel: "Delete", danger: true });
  if (yes) {
    await api.delete("/admin/notices/" + notice.id);
    toast("Deleted");
    load();
  }
}

function audience(notice) {
  if (!notice.targetBranch && !notice.targetSemester) {
    return "Everyone";
  }
  return [notice.targetBranch || "All branches", notice.targetSemester ? "Semester " + notice.targetSemester : "all semesters"].join(" · ");
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  loadInto(list, () => api.get(`/admin/notices?size=20&page=${pageNumber}`), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 notice" : page.totalElements + " notices"),
      dataTable(
        [
          { label: "Notice", cell: (notice) => h("div.stack.stack--tight", h("strong", notice.title), h("span.row__meta", audience(notice))) },
          { label: "Category", cell: (notice) => categoryTag(notice.category) },
          { label: "Due", cell: (notice) => (notice.dueDate ? formatDate(notice.dueDate) : "—") },
          { label: "Status", cell: (notice) => (notice.status === "PUBLISHED" ? tag("Published", "success", "eye") : tag("Draft", "", "edit")) },
          {
            label: "Actions",
            align: "end",
            cell: (notice) =>
              h(
                "span.row-actions",
                notice.status === "PUBLISHED" ? rowAction("Return to draft: " + notice.title, "undo", () => setPublished(notice, false)) : rowAction("Publish: " + notice.title, "send", () => setPublished(notice, true)),
                rowAction("Edit: " + notice.title, "edit", () => noticeForm(notice)),
                rowAction("Delete: " + notice.title, "trash", () => remove(notice), "danger"),
              ),
          },
        ],
        page.content,
        "No notices yet. Write the first one with the button above.",
      ),
      pager(page, (target) => {
        pageNumber = target;
        load();
      }),
    );
  });
}

const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "New notice");
add.addEventListener("click", () => noticeForm(null));
fill(document.getElementById("actions"), add);
fill(content, h("section.card", { id: "list", "aria-label": "Notices" }, h("div.skeleton")));
load();
