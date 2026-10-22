import { confirmAction, dataTable, field, hidePageError, input, numberSelect, openForm, pager, rowAction, select, semesterOptions, textarea } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { categoryLabels, categoryTag, loadInto } from "./parts.js";
import "./shell.js";

// Admin: notices (FR-ADM-006, FR-NOT-002). A notice is written as a draft, then published. It can
// be withdrawn to a draft again, edited, or deleted, and it can carry the official document (the
// scanned circular, PDF or picture) that students open from the notice.

const content = document.getElementById("content");
let pageNumber = 0;

function noticeForm(notice) {
  // The file is sent separately after the notice is saved, so it has no "name" in the form.
  const file = h("input.textbox", { id: "f-document", type: "file", accept: "application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png" });
  const removeBox = notice?.documentType ? h("input", { id: "f-remove-document", type: "checkbox" }) : null;
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
      h(
        "div.field--wide",
        field(notice?.documentType ? "Replace the official document (optional)" : "Official document (optional)", file, "The college's circular as a PDF, or a photo or scan (JPEG or PNG), up to 10 MB. Students open it from the notice."),
      ),
      removeBox ? h("label.line.line--start.field--wide", { for: "f-remove-document" }, removeBox, "Remove the current official document") : null,
    ],
    wide: true,
    onSubmit: async (values) => {
      const saved = notice ? await api.put("/admin/notices/" + notice.id, values) : await api.post("/admin/notices", values);
      if (file.files[0]) {
        try {
          await api.postFile(`/admin/notices/${saved.id}/document`, file.files[0]);
        } catch (failure) {
          // The notice is saved; say why the document is not.
          load();
          throw failure;
        }
      } else if (removeBox?.checked) {
        await api.delete(`/admin/notices/${saved.id}/document`);
      }
      toast("Saved");
      load();
    },
  }).querySelector("textarea").value = notice?.body || "";
}

async function openDocument(notice) {
  try {
    const link = await api.get(`/admin/notices/${notice.id}/document`);
    window.open(link.url, "_blank", "noopener");
  } catch (failure) {
    toast(messageFor(failure));
  }
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
          { label: "Official notice", cell: (notice) => (notice.documentType ? tag(notice.documentType === "application/pdf" ? "PDF" : "Picture", "primary", "file") : "—") },
          { label: "Status", cell: (notice) => (notice.status === "PUBLISHED" ? tag("Published", "success", "eye") : tag("Draft", "", "edit")) },
          {
            label: "Actions",
            align: "end",
            cell: (notice) =>
              h(
                "span.row-actions",
                notice.status === "PUBLISHED" ? rowAction("Return to draft: " + notice.title, "undo", () => setPublished(notice, false)) : rowAction("Publish: " + notice.title, "send", () => setPublished(notice, true)),
                notice.documentType ? rowAction("Open the official notice: " + notice.title, "external", () => openDocument(notice)) : null,
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
