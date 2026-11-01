// A saved chat is a flat list of messages. This puts each question together with the answers saved
// for it, one for each answer mode, so the Quick Assistant can show the same question with its
// Quick Answer and its Explanatory Answer and switch between them without asking the model again.
// No DOM here, so it can be tested.

export const STYLES = ["quick", "explain"];

/**
 * Groups messages by question. An answer belongs to the question its replyTo names; an answer with
 * no replyTo (a chat saved by an older version) belongs to the question just before it. Where a
 * mode has more than one answer, the first one saved is kept. Each group has the question, the
 * answers by mode, and {@code last}: the mode of the answer written last.
 */
export function groupMessages(messages) {
  const groups = [];
  const byQuestion = new Map();
  let current = null;
  for (const message of messages) {
    if (message.role === "user") {
      current = { question: message, answers: {}, last: null };
      groups.push(current);
      if (message.id != null) byQuestion.set(message.id, current);
    } else if (message.role === "ai") {
      const group = (message.replyTo != null && byQuestion.get(message.replyTo)) || current;
      if (!group) continue;
      const style = message.style === "explain" ? "explain" : "quick";
      group.answers[style] ??= message;
      group.last = style;
    }
  }
  return groups;
}

/** The mode of a group that is shown: the one chosen, if it has an answer, else the last written. */
export function shownStyle(group, chosen) {
  return chosen && group.answers[chosen] ? chosen : (group.last ?? "quick");
}

/** The mode of the answer written last in the whole chat, or null for an empty chat. */
export function lastStyle(messages) {
  const groups = groupMessages(messages);
  for (let i = groups.length - 1; i >= 0; i--) if (groups[i].last) return groups[i].last;
  return null;
}
