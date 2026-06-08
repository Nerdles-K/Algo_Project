/**
 * Avatar color palette - diverse set of vibrant colors
 */
const AVATAR_COLORS = [
  "#53c0f0", // cyan
  "#10b981", // emerald
  "#f59e0b", // amber
  "#ef4444", // red
  "#8b5cf6", // violet
  "#ec4899", // pink
  "#06b6d4", // sky
  "#14b8a6", // teal
  "#f97316", // orange
  "#6366f1", // indigo
  "#d946ef", // fuchsia
  "#0891b2", // cyan-dark
];

/**
 * Get a consistent color for a given string (e.g., channel name)
 * Uses hash to ensure the same string always returns the same color
 * @param {string} str - The string to hash (e.g., channel name)
 * @returns {string} A hex color code
 */
export function getAvatarColor(str) {
  if (!str) return AVATAR_COLORS[0];

  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash; // Convert to 32bit integer
  }

  const index = Math.abs(hash) % AVATAR_COLORS.length;
  return AVATAR_COLORS[index];
}

/**
 * Get complementary gradient colors for an avatar
 * @param {string} str - The string to hash
 * @returns {Array<string>} Array of two gradient colors
 */
export function getAvatarGradient(str) {
  const colors = [
    ["#53c0f0", "#2b9fd9"], // cyan gradient
    ["#10b981", "#059669"], // emerald gradient
    ["#f59e0b", "#d97706"], // amber gradient
    ["#ef4444", "#dc2626"], // red gradient
    ["#8b5cf6", "#7c3aed"], // violet gradient
    ["#ec4899", "#db2777"], // pink gradient
    ["#06b6d4", "#0891b2"], // sky gradient
    ["#14b8a6", "#0d9488"], // teal gradient
    ["#f97316", "#ea580c"], // orange gradient
    ["#6366f1", "#4f46e5"], // indigo gradient
    ["#d946ef", "#c026d3"], // fuchsia gradient
    ["#0891b2", "#0e7490"], // cyan-dark gradient
  ];

  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }

  const index = Math.abs(hash) % colors.length;
  return colors[index];
}
