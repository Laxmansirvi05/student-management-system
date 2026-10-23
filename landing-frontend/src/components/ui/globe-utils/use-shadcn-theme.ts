export type ThemeMode = "light" | "dark" | "auto";

export interface ShadcnThemeColors {
  primaryColor: string;
  mutedColor: string;
  resolvedTheme: "light" | "dark";
}

/**
 * Returns color values usable by Three.js materials matching Opportunity Radar & Campus Companion.
 * Uses Opportunity Radar's electric blue (#2563eb) for particles and orbital rings,
 * and subtle navy (#0f172a) for inner sphere and muted details.
 */
export function useShadcnTheme(theme: ThemeMode = "auto"): ShadcnThemeColors {
  const isDark = theme === "dark";

  if (isDark) {
    return {
      primaryColor: "#38bdf8",
      mutedColor: "#1e293b",
      resolvedTheme: "dark",
    };
  }

  return {
    primaryColor: "#1d4ed8",
    mutedColor: "#0f172a",
    resolvedTheme: "light",
  };
}

export default useShadcnTheme;
