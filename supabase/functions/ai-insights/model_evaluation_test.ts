import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { parseModelEvaluationConfig } from "./model_evaluation.ts";

Deno.test("model evaluation requires at least two bounded model choices and prices", () => {
  const prices = JSON.stringify({ a: { inputUsdPer1M: 1, outputUsdPer1M: 2 }, b: { inputUsdPer1M: 3, outputUsdPer1M: 4 } });
  assertEquals(parseModelEvaluationConfig("a", prices, 0.1), null);
  assertEquals(parseModelEvaluationConfig("a,b", prices, 0.1)?.models, ["a", "b"]);
  assertEquals(parseModelEvaluationConfig("a,b,a", prices, 0.1)?.models, ["a", "b"]);
  assertEquals(parseModelEvaluationConfig("a,b", prices, 0), null);
});

Deno.test("model evaluation rejects missing, negative, non-finite, and invalid prices", () => {
  assertEquals(parseModelEvaluationConfig("a,b", "{", 1), null);
  assertEquals(parseModelEvaluationConfig("a,b", JSON.stringify({ a: { inputUsdPer1M: 1, outputUsdPer1M: 1 } }), 1), null);
  assertEquals(parseModelEvaluationConfig("a,b", JSON.stringify({
    a: { inputUsdPer1M: -1, outputUsdPer1M: 1 }, b: { inputUsdPer1M: 1, outputUsdPer1M: 1 },
  }), 1), null);
  assertEquals(parseModelEvaluationConfig("a,b", JSON.stringify({
    a: { inputUsdPer1M: 0, outputUsdPer1M: 0 }, b: { inputUsdPer1M: 1, outputUsdPer1M: 1 },
  }), 1)?.prices.a, { inputUsdPer1M: 0, outputUsdPer1M: 0 });
});
