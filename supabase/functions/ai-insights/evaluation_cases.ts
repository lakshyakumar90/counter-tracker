import type { Summary } from "./policy.ts";

/** Synthetic, provider-free policy fixtures. No real account or route data is used. */
export const evaluationCases: Array<{
  name: string;
  summary: Summary;
  workout: Record<string, unknown>;
}> = [
  {
    name: "novice_return_to_running",
    summary: { periodDays: 28, activityCount: 2, distanceMeters: 4_200, movingSeconds: 2_700, sports: ["RUNNING"], focus: "consistency" },
    workout: { name: "Easy run", sport: "RUNNING", durationMinutes: 20, rationale: "Keep the session relaxed and build a steady routine.", blocks: [
      { type: "WARMUP", durationMinutes: 5, effort: 2 }, { type: "WORK", durationMinutes: 10, effort: 3 }, { type: "COOLDOWN", durationMinutes: 5, effort: 2 },
    ] },
  },
  {
    name: "intermediate_distance_focus",
    summary: { periodDays: 28, activityCount: 10, distanceMeters: 42_000, movingSeconds: 18_000, sports: ["RUNNING", "CYCLING"], focus: "distance" },
    workout: { name: "Steady aerobic run", sport: "RUNNING", durationMinutes: 35, rationale: "Use a controlled effort and finish feeling comfortable.", blocks: [
      { type: "WARMUP", durationMinutes: 8, effort: 3 }, { type: "WORK", durationMinutes: 22, effort: 5 }, { type: "COOLDOWN", durationMinutes: 5, effort: 2 },
    ] },
  },
  {
    name: "recovery_focus_after_regular_training",
    summary: { periodDays: 28, activityCount: 14, distanceMeters: 61_000, movingSeconds: 25_000, sports: ["RUNNING", "WALKING"], focus: "recovery" },
    workout: { name: "Gentle recovery walk", sport: "WALKING", durationMinutes: 25, rationale: "Keep this session easy and shorten it if you feel tired.", blocks: [
      { type: "WARMUP", durationMinutes: 5, effort: 2 }, { type: "WORK", durationMinutes: 15, effort: 3 }, { type: "COOLDOWN", durationMinutes: 5, effort: 2 },
    ] },
  },
];
