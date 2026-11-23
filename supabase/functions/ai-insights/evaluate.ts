import { evaluationCases } from "./evaluation_cases.ts";
import { generateWithProvider } from "./provider.ts";
import { parseModelEvaluationConfig } from "./model_evaluation.ts";

const provider = Deno.env.get("PACE_AI_EVAL_PROVIDER") ?? "openrouter";
const apiKey = provider === "gemini" ? Deno.env.get("GEMINI_API_KEY") : Deno.env.get("OPENROUTER_API_KEY");
const maximumCostUsd = Number(Deno.env.get("PACE_AI_EVAL_MAX_COST_USD"));
const config = parseModelEvaluationConfig(
  Deno.env.get("PACE_AI_EVAL_MODELS") ?? "",
  Deno.env.get("PACE_AI_EVAL_PRICING_JSON") ?? "",
  maximumCostUsd,
);

if (!apiKey || !config || (provider !== "openrouter" && provider !== "gemini")) {
  console.error("Set the selected provider key, PACE_AI_EVAL_PROVIDER (openrouter or gemini), at least two PACE_AI_EVAL_MODELS, valid per-model PACE_AI_EVAL_PRICING_JSON, and positive PACE_AI_EVAL_MAX_COST_USD. No request was sent.");
  Deno.exitCode = 2;
} else {
  const rows: Array<Record<string, unknown>> = [];
  let reservedCostUsd = 0;
  for (const model of config.models) {
    const price = config.prices[model];
    for (const testCase of evaluationCases) {
      for (const kind of ["insight", "workout"] as const) {
        const reservedCost = (10_000 * price.inputUsdPer1M + 900 * price.outputUsdPer1M) / 1_000_000;
        if (reservedCostUsd + reservedCost > config.maxCostUsd) {
          rows.push({ model, case: testCase.name, kind, status: "evaluation_cost_ceiling" });
          continue;
        }
        reservedCostUsd += reservedCost;
        const startedAt = performance.now();
        try {
          const generated = await generateWithProvider(provider, apiKey, model, kind, testCase.summary);
          const elapsedMs = Math.round(performance.now() - startedAt);
          if (!Number.isSafeInteger(generated.inputTokens) || generated.inputTokens <= 0 ||
            !Number.isSafeInteger(generated.outputTokens) || generated.outputTokens <= 0) {
            rows.push({ model, case: testCase.name, kind, status: "missing_usage", elapsedMs });
            continue;
          }
          const costUsd = (generated.inputTokens * price.inputUsdPer1M + generated.outputTokens * price.outputUsdPer1M) / 1_000_000;
          rows.push({ model, case: testCase.name, kind, status: "schema_and_policy_pass", elapsedMs, inputTokens: generated.inputTokens, outputTokens: generated.outputTokens, costUsd });
        } catch (error) {
          const message = error instanceof Error ? error.message : "";
          const status = error instanceof DOMException && error.name === "AbortError"
            ? "timeout"
            : message.includes("invalid") || message.includes("mismatch")
            ? "schema_or_policy_rejected"
            : message.includes("refused")
            ? "refused"
            : "provider_error";
          rows.push({ model, case: testCase.name, kind, status, elapsedMs: Math.round(performance.now() - startedAt) });
        }
      }
    }
  }

  const byModel = config.models.map((model) => {
    const modelRows = rows.filter((row) => row.model === model);
    const passed = modelRows.filter((row) => row.status === "schema_and_policy_pass");
    const durations = passed.map((row) => Number(row.elapsedMs));
    return {
      model,
      cases: modelRows.length,
      schemaAndPolicyPasses: passed.length,
      passRate: modelRows.length === 0 ? 0 : passed.length / modelRows.length,
      meanLatencyMs: durations.length === 0 ? null : Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length),
      totalCostUsd: Number(passed.reduce((sum, row) => sum + Number(row.costUsd), 0).toFixed(8)),
      inputTokens: passed.reduce((sum, row) => sum + Number(row.inputTokens), 0),
      outputTokens: passed.reduce((sum, row) => sum + Number(row.outputTokens), 0),
    };
  });

  console.log(JSON.stringify({
    scope: "synthetic fixtures only; no user data",
    provider,
    estimatedUpperBoundCostUsd: Number(reservedCostUsd.toFixed(8)),
    configuredMaximumCostUsd: config.maxCostUsd,
    byModel,
    rows,
  }, null, 2));
  if (rows.some((row) => row.status !== "schema_and_policy_pass")) Deno.exitCode = 1;
}
