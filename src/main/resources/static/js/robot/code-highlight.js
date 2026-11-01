// A small syntax highlighter for the code blocks of an answer (no DOM, no library, so it can be
// tested). It cuts code into pieces of a kind (comment, string, number, keyword, type, tag...) with
// plain regular expressions for a few common languages; any other language, or a block it cannot
// read, comes back as one plain piece. The text is never changed: joining the pieces gives the code
// back exactly, and the renderer sets each piece as text.

const KEYWORDS = {
  java: "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long new null package private protected public return short static super switch synchronized this throw throws try var void volatile while true false record yield",
  javascript: "async await break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new null of return static super switch this throw try typeof undefined var void while with yield true false",
  typescript: "abstract any as async await boolean break case catch class const continue declare default delete do else enum export extends finally for function if implements import in instanceof interface let namespace never new null number of private protected public readonly return static string super switch this throw try type typeof undefined unknown var void while yield true false",
  python: "and as assert async await break class continue def del elif else except finally for from global if import in is lambda None nonlocal not or pass raise return try while with yield True False self",
  c: "auto break case char const continue default do double else enum extern float for goto if inline int long register return short signed sizeof static struct switch typedef union unsigned void volatile while NULL",
  cpp: "alignas auto bool break case catch char class const constexpr continue default delete do double else enum explicit export extern false float for friend goto if inline int long mutable namespace new noexcept nullptr operator private protected public register return short signed sizeof static struct switch template this throw true try typedef typename union unsigned using virtual void volatile while",
  csharp: "abstract as base bool break byte case catch char class const continue decimal default delegate do double else enum event false finally float for foreach if in int interface internal is lock long namespace new null object operator out override private protected public readonly ref return sealed short static string struct switch this throw true try typeof uint ulong using var virtual void while",
  go: "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var nil true false",
  rust: "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while",
  sql: "add all alter and as asc between by case check column constraint create cross database default delete desc distinct drop else end exists foreign from full group having in index inner insert into is join key left like limit not null on or order outer primary references right select set table then union unique update values view when where with",
  bash: "if then else elif fi for while do done case esac function in echo export cd ls grep cat sudo",
  css: "",
  json: "true false null",
};

const TYPES = {
  java: "String Integer Long Double Float Boolean Character Object List ArrayList Map HashMap Set HashSet Scanner System Math",
  typescript: "Array Promise Record Map Set Date",
  csharp: "String List Dictionary Console Math",
};

const ALIASES = { js: "javascript", jsx: "javascript", mjs: "javascript", ts: "typescript", tsx: "typescript", py: "python", python3: "python", "c++": "cpp", cc: "cpp", cxx: "cpp", "c#": "csharp", cs: "csharp", golang: "go", rs: "rust", sh: "bash", shell: "bash", zsh: "bash", mysql: "sql", postgres: "sql", postgresql: "sql", sqlite: "sql", html: "html", xml: "html", jsonc: "json" };

const COMMENTS = {
  java: ["//", "/*"], javascript: ["//", "/*"], typescript: ["//", "/*"], c: ["//", "/*"], cpp: ["//", "/*"], csharp: ["//", "/*"], go: ["//", "/*"], rust: ["//", "/*"], css: ["/*"],
  python: ["#"], bash: ["#"], sql: ["--", "/*"], json: [], html: [],
};

function words(list) {
  return new Set(list.split(/\s+/).filter(Boolean));
}

const KEYWORD_SETS = Object.fromEntries(Object.entries(KEYWORDS).map(([language, list]) => [language, words(list)]));
const TYPE_SETS = Object.fromEntries(Object.entries(TYPES).map(([language, list]) => [language, words(list)]));

/** The language this highlighter knows by the label an answer gave, or null. */
export function languageOf(label) {
  const name = String(label ?? "").trim().toLowerCase();
  const language = ALIASES[name] ?? name;
  return language in KEYWORDS || language === "html" ? language : null;
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Cuts code into [{type, text}] pieces: comment, string, number, keyword, type, tag, attr or plain. */
export function highlight(code, label) {
  const language = languageOf(label);
  const text = String(code ?? "");
  if (!language || text.length > 20000) return [{ type: "plain", text }];
  if (language === "html") return highlightMarkup(text);
  const parts = [];
  const comments = (COMMENTS[language] ?? []).map((mark) => (mark === "/*" ? "\\/\\*[\\s\\S]*?(?:\\*\\/|$)" : escapeRegex(mark) + "[^\\n]*"));
  const quote = language === "python" ? "\"\"\"[\\s\\S]*?(?:\"\"\"|$)|'''[\\s\\S]*?(?:'''|$)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?" : language === "javascript" || language === "typescript" ? "`(?:\\\\[\\s\\S]|[^`\\\\])*`?|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?" : "\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?";
  const pattern = new RegExp(["(" + (comments.join("|") || "(?!)") + ")", "(" + quote + ")", "(\\b0[xX][0-9a-fA-F_]+\\b|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLdD]?\\b)", "([A-Za-z_$][\\w$]*)"].join("|"), "g");
  let last = 0;
  const push = (type, value) => {
    if (!value) return;
    const previous = parts[parts.length - 1];
    if (previous && previous.type === type) previous.text += value;
    else parts.push({ type, text: value });
  };
  for (const match of text.matchAll(pattern)) {
    push("plain", text.slice(last, match.index));
    last = match.index + match[0].length;
    if (match[1] !== undefined) push("comment", match[0]);
    else if (match[2] !== undefined) push("string", match[0]);
    else if (match[3] !== undefined) push("number", match[0]);
    else {
      const word = match[4];
      const key = language === "sql" ? word.toLowerCase() : word;
      if (KEYWORD_SETS[language]?.has(key)) push("keyword", word);
      else if (TYPE_SETS[language]?.has(word) || (/^[A-Z][A-Za-z0-9_]*$/.test(word) && ["java", "typescript", "csharp", "cpp"].includes(language) && word.length > 1)) push("type", word);
      else push("plain", word);
    }
  }
  push("plain", text.slice(last));
  return parts;
}

function highlightMarkup(text) {
  const parts = [];
  const pattern = /(<!--[\s\S]*?(?:-->|$))|(<\/?[A-Za-z][\w:-]*)([^<>]*?)(\/?>)|("[^"]*"|'[^']*')/g;
  let last = 0;
  const push = (type, value) => {
    if (value) parts.push({ type, text: value });
  };
  for (const match of text.matchAll(pattern)) {
    push("plain", text.slice(last, match.index));
    last = match.index + match[0].length;
    if (match[1] !== undefined) push("comment", match[1]);
    else if (match[2] !== undefined) {
      push("tag", match[2]);
      for (const piece of match[3].split(/(\s+|"[^"]*"|'[^']*')/).filter((item) => item !== "")) {
        push(/^["']/.test(piece) ? "string" : /^\s+$/.test(piece) ? "plain" : "attr", piece);
      }
      push("tag", match[4]);
    } else push("plain", match[5]);
  }
  push("plain", text.slice(last));
  return parts;
}
