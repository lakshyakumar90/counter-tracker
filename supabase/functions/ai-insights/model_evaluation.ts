export type ModelPrice = { inputUsdPer1M: number; outputUsdPer1M: number };
export type ModelEvaluationConfig = { models: string[]; prices: Record<string, ModelPrice>; maxCostUsd: number };

/** Validates a bounded comparison configuration before any provider calls. */
export function parseModelEvaluationConfig(modelsCsv: string, pricesJson: string, maxCostUsd: number): ModelEvaluationConfig | null {
  const models = [...new Set(modelsCsv.split(",").map((model) => model.trim()).filter(Boolean))];
  if (models.length < 2 || models.length > 6 || models.some((model) => model.length > 100) ||
    !Number.isFinite(maxCostUsd) || maxCostUsd <= 0) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(pricesJson);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
  const entries = parsed as Record<string, unknown>;
  const prices: Record<string, ModelPrice> = {};
  for (const model of models) {
    const price = entries[model];
    if (!price || typeof price !== "object" || Array.isArray(price)) return null;
    const row = price as Record<string, unknown>;
    if (typeof row.inputUsdPer1M !== "number" || !Number.isFinite(row.inputUsdPer1M) || row.inputUsdPer1M < 0 ||
      typeof row.outputUsdPer1M !== "number" || !Number.isFinite(row.outputUsdPer1M) || row.outputUsdPer1M < 0) return null;
    prices[model] = { inputUsdPer1M: row.inputUsdPer1M, outputUsdPer1M: row.outputUsdPer1M };
  }
  return { models, prices, maxCostUsd };
}
