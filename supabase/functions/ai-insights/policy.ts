export type Summary = {
  periodDays: number;
  activityCount: number;
  distanceMeters: number;
  movingSeconds: number;
  sports: string[];
  focus: "consistency" | "distance" | "recovery" | "strength";
};

const SUPPORTED_SPORTS = ["CYCLING", "RUNNING", "JOGGING", "WALKING", "HOME_WORKOUT"];

/** Whitelists the small aggregate sent to the model; all unknown input fields are discarded. */
export function validateSummary(value: unknown): Summary | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const row = value as Record<string, unknown>;
  if (!Number.isInteger(row.periodDays) || Number(row.periodDays) < 7 || Number(row.periodDays) > 90) return null;
  if (!Number.isInteger(row.activityCount) || Number(row.activityCount) < 0 || Number(row.activityCount) > 300) return null;
  if (!Number.isFinite(row.distanceMeters) || Number(row.distanceMeters) < 0 || Number(row.distanceMeters) > 2_000_000) return null;
  if (!Number.isInteger(row.movingSeconds) || Number(row.movingSeconds) < 0 || Number(row.movingSeconds) > 50_000_000) return null;
  if (!Array.isArray(row.sports) || row.sports.length > 5 || !row.sports.every((sport) => SUPPORTED_SPORTS.includes(String(sport)))) return null;
  if (!["consistency", "distance", "recovery", "strength"].includes(String(row.focus))) return null;
  return {
    periodDays: Number(row.periodDays), activityCount: Number(row.activityCount),
    distanceMeters: Number(row.distanceMeters), movingSeconds: Number(row.movingSeconds),
    sports: row.sports.map(String), focus: row.focus as Summary["focus"],
  };
}

export function validateResult(kind: "insight" | "workout", result: Record<string, unknown>, summary: Summary): void {
  if (kind === "insight") {
    if (typeof result.title !== "string" || result.title.length > 80 || typeof result.summary !== "string" || result.summary.length > 500 ||
      !Array.isArray(result.observations) || result.observations.length > 5 || !result.observations.every((item) => typeof item === "string" && item.length <= 220) ||
      typeof result.suggestedAction !== "string" || result.suggestedAction.length > 300) throw new Error("Insight response invalid");
    const supportedNumbers = new Set([
      summary.periodDays, summary.activityCount, summary.distanceMeters, summary.movingSeconds,
      Number((summary.distanceMeters / 1_000).toFixed(1)), Number((summary.movingSeconds / 60).toFixed(1)),
    ]);
    const text = [result.title, result.summary, ...result.observations, result.suggestedAction].join(" ");
    for (const match of text.matchAll(/(?<![\w.])\d+(?:,\d{3})*(?:\.\d+)?(?![\w.])/g)) {
      if (!supportedNumbers.has(Number(match[0].replaceAll(",", "")))) throw new Error("Insight contains unsupported numerical claim");
    }
    return;
  }
  if (typeof result.name !== "string" || result.name.length > 80 || ![...summary.sports, "HOME_WORKOUT"].includes(String(result.sport)) ||
    !Number.isInteger(result.durationMinutes) || Number(result.durationMinutes) < 10 || Number(result.durationMinutes) > 120 ||
    typeof result.rationale !== "string" || result.rationale.length > 300 || !Array.isArray(result.blocks) || result.blocks.length < 1 || result.blocks.length > 12) {
    throw new Error("Workout response invalid");
  }
  let total = 0;
  for (const block of result.blocks as Array<Record<string, unknown>>) {
    if (!block || typeof block !== "object" || !["WARMUP", "WORK", "RECOVERY", "COOLDOWN"].includes(String(block.type)) ||
      !Number.isInteger(block.durationMinutes) || Number(block.durationMinutes) < 1 || Number(block.durationMinutes) > 120 ||
      !Number.isInteger(block.effort) || Number(block.effort) < 1 || Number(block.effort) > 10) throw new Error("Workout block invalid");
    total += Number(block.durationMinutes);
  }
  if (total !== Number(result.durationMinutes)) throw new Error("Workout duration mismatch");
}
