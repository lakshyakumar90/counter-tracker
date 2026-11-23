import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.1";
import { validateSummary } from "./policy.ts";
import { AI_PROMPT_VERSION } from "./prompts.ts";
import { generateWithFallback } from "./provider.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" } });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json(405, { error: "METHOD_NOT_ALLOWED" });
  const token = request.headers.get("Authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) return json(401, { error: "AUTH_REQUIRED" });

  const rawBody = await request.text();
  if (rawBody.length > 16_384) return json(413, { error: "REQUEST_TOO_LARGE" });
  let parsed: unknown;
  try { parsed = JSON.parse(rawBody); } catch { return json(400, { error: "INVALID_REQUEST" }); }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return json(400, { error: "INVALID_REQUEST" });
  const requestBody = parsed as Record<string, unknown>;
  const kind = requestBody.kind;
  const summary = validateSummary(requestBody.summary);
  if ((kind !== "insight" && kind !== "workout") || !summary) return json(400, { error: "INVALID_REQUEST" });

  const url = Deno.env.get("SUPABASE_URL");
  const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const openRouterKey = Deno.env.get("OPENROUTER_API_KEY") ?? "";
  const openRouterModels = (Deno.env.get("PACE_AI_OPENROUTER_MODELS") ?? "openrouter/free")
    .split(",").map((model) => model.trim()).filter(Boolean);
  const geminiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
  const geminiModel = Deno.env.get("PACE_AI_GEMINI_MODEL") ?? "gemini-3.5-flash-lite";
  const geminiFreeTierOnly = Deno.env.get("PACE_AI_GEMINI_FREE_TIER_ONLY") === "true";
  const inputRate = Number(Deno.env.get("PACE_AI_INPUT_USD_PER_1M") ?? "0");
  const outputRate = Number(Deno.env.get("PACE_AI_OUTPUT_USD_PER_1M") ?? "0");
  const budget = Number(Deno.env.get("PACE_AI_MONTHLY_BUDGET_USD"));
  const monthlyLimit = Number(Deno.env.get("PACE_AI_MONTHLY_REQUEST_LIMIT"));
  const userMonthlyLimit = Number(Deno.env.get("PACE_AI_USER_MONTHLY_REQUEST_LIMIT"));
  if (!url || !publicKey || !serviceKey || (!openRouterKey && !geminiKey) ||
      (geminiKey && !geminiFreeTierOnly) ||
      openRouterModels.length > 2 || openRouterModels.some((model) => model !== "openrouter/free" && !model.endsWith(":free")) ||
      !Number.isFinite(inputRate) || inputRate < 0 || !Number.isFinite(outputRate) || outputRate < 0 ||
      !Number.isFinite(budget) || budget <= 0 || !Number.isInteger(monthlyLimit) || monthlyLimit < 1 ||
      !Number.isInteger(userMonthlyLimit) || userMonthlyLimit < 1) {
    return json(503, { error: "AI_NOT_CONFIGURED" });
  }

  const userClient = createClient(url, publicKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: { user }, error: authError } = await userClient.auth.getUser(token);
  if (authError || !user) return json(401, { error: "SESSION_INVALID" });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: control, error: controlError } = await admin.from("pace_ai_control")
    .select("enabled").eq("id", true).maybeSingle();
  if (controlError) return json(503, { error: "AI_CONTROL_CHECK_FAILED" });
  if (!control?.enabled) return json(503, { error: "AI_DISABLED" });
  const { data: preference, error: preferenceError } = await admin.from("pace_ai_preferences")
    .select("opted_in").eq("user_id", user.id).maybeSingle();
  if (preferenceError) return json(503, { error: "CONSENT_CHECK_FAILED" });
  if (!preference?.opted_in) return json(403, { error: "AI_OPT_IN_REQUIRED" });

  // The request body is bounded to 16 KiB; reserve conservatively for prompt and schema overhead.
  const reservedCost = (10_000 * inputRate + 900 * outputRate) / 1_000_000;
  const configuredDailyLimit = Number(Deno.env.get("PACE_AI_DAILY_REQUEST_LIMIT") ?? "3");
  if (!Number.isInteger(configuredDailyLimit) || configuredDailyLimit < 1 || configuredDailyLimit > 10) return json(503, { error: "AI_NOT_CONFIGURED" });
  const dailyLimit = configuredDailyLimit;
  const { data: reservation, error: reserveError } = await admin.rpc("pace_reserve_ai_request", {
    p_user_id: user.id, p_kind: kind, p_daily_limit: dailyLimit,
    p_monthly_request_limit: monthlyLimit, p_user_monthly_request_limit: userMonthlyLimit,
    p_prompt_version: AI_PROMPT_VERSION,
    p_monthly_budget_usd: budget,
    p_reserved_cost_usd: reservedCost,
  });
  if (reserveError) return json(503, { error: "QUOTA_CHECK_FAILED" });
  const reservationId = reservation as string | null;
  if (!reservationId) return json(429, { error: "AI_QUOTA_REACHED" });

  try {
    const ai = await generateWithFallback({ openRouterKey, openRouterModels, geminiKey, geminiModel }, kind, summary);
    const actualCost = (ai.inputTokens * inputRate + ai.outputTokens * outputRate) / 1_000_000;
    await admin.rpc("pace_finish_ai_request", {
      p_reservation_id: reservationId, p_input_tokens: ai.inputTokens,
      p_output_tokens: ai.outputTokens, p_actual_cost_usd: actualCost, p_succeeded: true,
    });
    return json(200, { kind, result: ai.result });
  } catch (error) {
    await admin.rpc("pace_finish_ai_request", {
      p_reservation_id: reservationId, p_input_tokens: 0, p_output_tokens: 0,
      p_actual_cost_usd: reservedCost, p_succeeded: false,
    });
    const code = error instanceof DOMException && error.name === "AbortError" ? "AI_TIMEOUT" : "AI_UNAVAILABLE";
    return json(code === "AI_TIMEOUT" ? 504 : 502, { error: code });
  }
});

