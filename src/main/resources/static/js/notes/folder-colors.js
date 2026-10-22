// The folder palette. Folder colour is identity, not decoration, so each palette is one hue at four
// depths. Fixed values rather than classes because the colour is chosen at run time.

export const FOLDER_COLORS = ["blue", "cyan", "purple", "indigo", "green", "yellow", "orange", "red", "pink", "teal", "neutral"];

export const FOLDER_PALETTES = {
  blue: { back: "#1e40af", tab: "#2563eb", frontFrom: "#60a5fa", frontTo: "#3b82f6", glow: "rgba(37,99,235,0.45)", ink: "#0b1f4d", label: "Blue" },
  cyan: { back: "#155e75", tab: "#0891b2", frontFrom: "#67e8f9", frontTo: "#22d3ee", glow: "rgba(8,145,178,0.45)", ink: "#083344", label: "Cyan" },
  purple: { back: "#5b21b6", tab: "#7c3aed", frontFrom: "#c4b5fd", frontTo: "#a78bfa", glow: "rgba(124,58,237,0.45)", ink: "#2e1065", label: "Purple" },
  indigo: { back: "#3730a3", tab: "#4f46e5", frontFrom: "#a5b4fc", frontTo: "#818cf8", glow: "rgba(79,70,229,0.45)", ink: "#1e1b4b", label: "Indigo" },
  green: { back: "#166534", tab: "#16a34a", frontFrom: "#86efac", frontTo: "#4ade80", glow: "rgba(22,163,74,0.45)", ink: "#052e16", label: "Green" },
  yellow: { back: "#a16207", tab: "#ca8a04", frontFrom: "#fde68a", frontTo: "#fcd34d", glow: "rgba(202,138,4,0.45)", ink: "#422006", label: "Yellow" },
  orange: { back: "#9a3412", tab: "#ea580c", frontFrom: "#fdba74", frontTo: "#fb923c", glow: "rgba(234,88,12,0.45)", ink: "#431407", label: "Orange" },
  red: { back: "#991b1b", tab: "#dc2626", frontFrom: "#fca5a5", frontTo: "#f87171", glow: "rgba(220,38,38,0.45)", ink: "#450a0a", label: "Red" },
  pink: { back: "#9d174d", tab: "#db2777", frontFrom: "#f9a8d4", frontTo: "#f472b6", glow: "rgba(219,39,119,0.45)", ink: "#500724", label: "Pink" },
  teal: { back: "#115e59", tab: "#0d9488", frontFrom: "#5eead4", frontTo: "#2dd4bf", glow: "rgba(13,148,136,0.45)", ink: "#042f2e", label: "Teal" },
  neutral: { back: "#334155", tab: "#475569", frontFrom: "#cbd5e1", frontTo: "#94a3b8", glow: "rgba(71,85,105,0.45)", ink: "#0f172a", label: "Neutral" },
};

export function paletteFor(color) {
  return FOLDER_PALETTES[color] || FOLDER_PALETTES.blue;
}
