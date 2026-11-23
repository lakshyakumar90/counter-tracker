import { validateResult, type Summary } from "./policy.ts";
import { systemPrompt } from "./prompts.ts";

export const RESPONSE_SCHEMAS = {
  insight: {
    type: "object", additionalProperties: false, required: ["title", "summary", "observations", "suggestedAction"],
    properties: {
      title: { type: "string" }, summary: { type: "string" },
      observations: { type: "array", items: { type: "string" } }, suggestedAction: { type: "string" },
    },
  },
  workout: {
    type: "object", additionalProperties: false, required: ["name", "sport", "durationMinutes", "rationale", "blocks"],
    properties: {
      name: { type: "string" },
      sport: { type: "string", enum: ["CYCLING", "RUNNING", "JOGGING", "WALKING", "HOME_WORKOUT"] },
      durationMinutes: { type: "integer" }, rationale: { type: "string" },
      blocks: {
        type: "array",
        items: {
          type: "object", additionalProperties: false, required: ["type", "durationMinutes", "effort"],
          properties: {
            type: { type: "string", enum: ["WARMUP", "WORK", "RECOVERY", "COOLDOWN"] },
            durationMinutes: { type: "integer" }, effort: { type: "integer" },
          },
        },
      },
    },
  },
} as const;

export type AiProvider = "openrouter" | "gemini";
export type GeneratedResponse = {
  result: Record<string, unknown>;
  inputTokens: number;
  outputTokens: number;
  provider: AiProvider;
  model: string;
};

export type ProviderConfig = {
  openRouterKey: string;
  openRouterModels: string[];
  geminiKey: string;
  geminiModel: string;
};

type ProviderAttempt = { provider: AiProvider; key: string; model: string };

function geminiSchema(value: unknown): unknown {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const schema = value as Record<string, unknown>;
  // Gemini's responseSchema subset does not accept JSON Schema's
  // additionalProperties field. Strict policy validation still runs locally.
  return Object.fromEntries(Object.entries(schema)
    .filter(([key]) => key !== "additionalProperties")
    .map(([key, child]) => [key, key === "properties" && child && typeof child === "object"
      ? Object.fromEntries(Object.entries(child as Record<string, unknown>).map(([name, nested]) => [name, geminiSchema(nested)]))
      : key === "items" ? geminiSchema(child) : child]));
}

function readText(value: unknown, provider: string): string {
  if (typeof value !== "string" || value.length > 12_000) throw new Error(`${provider} response invalid`);
  return value;
}

async function requestOpenRouter(
  attempt: ProviderAttempt,
  kind: "insight" | "workout",
  summary: Summary,
  fetchProvider: typeof fetch,
) {
  const response = await fetchProvider("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${attempt.key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: attempt.model,
      messages: [
        { role: "system", content: systemPrompt(kind) },
        { role: "user", content: JSON.stringify({ kind, summary }) },
      ],
      max_tokens: 900,
      reasoning: { effort: "low" },
      provider: { data_collection: "deny", require_parameters: true, allow_fallbacks: true },
      response_format: { type: "json_schema", json_schema: { name: `pace_${kind}_v1`, strict: true, schema: RESPONSE_SCHEMAS[kind] } },
    }),
  });
  if (!response.ok) throw new Error("OpenRouter request failed");
  const body = await response.json();
  const choice = body.choices?.[0];
  if (choice?.finish_reason === "content_filter" || choice?.message?.refusal) throw new Error("OpenRouter refused request");
  const text = readText(choice?.message?.content, "OpenRouter");
  return { text, inputTokens: Number(body.usage?.prompt_tokens) || 0, outputTokens: Number(body.usage?.completion_tokens) || 0 };
}

async function requestGemini(
  attempt: ProviderAttempt,
  kind: "insight" | "workout",
  summary: Summary,
  fetchProvider: typeof fetch,
) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(attempt.model)}:generateContent`;
  const response = await fetchProvider(url, {
    method: "POST",
    headers: { "x-goog-api-key": attempt.key, "Content-Type": "application/json" },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: systemPrompt(kind) }] },
      contents: [{ role: "user", parts: [{ text: JSON.stringify({ kind, summary }) }] }],
      generationConfig: {
        maxOutputTokens: 900,
        responseMimeType: "application/json",
        responseSchema: geminiSchema(RESPONSE_SCHEMAS[kind]),
      },
    }),
  });
  if (!response.ok) throw new Error("Gemini request failed");
  const body = await response.json();
  const candidate = body.candidates?.[0];
  if (candidate?.finishReason && candidate.finishReason !== "STOP") throw new Error("Gemini response incomplete");
  const text = readText(candidate?.content?.parts?.map((part: { text?: string }) => part.text ?? "").join(""), "Gemini");
  return {
    text,
    inputTokens: Number(body.usageMetadata?.promptTokenCount) || 0,
    outputTokens: Number(body.usageMetadata?.candidatesTokenCount) || 0,
  };
}

async function generateFromAttempt(
  attempt: ProviderAttempt,
  kind: "insight" | "workout",
  summary: Summary,
  fetchProvider: typeof fetch,
): Promise<GeneratedResponse> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), attempt.provider === "openrouter" ? 7_000 : 10_000);
  try {
    const response = attempt.provider === "openrouter"
      ? await requestOpenRouter(attempt, kind, summary, (input, init) => fetchProvider(input, { ...init, signal: controller.signal }))
      : await requestGemini(attempt, kind, summary, (input, init) => fetchProvider(input, { ...init, signal: controller.signal }));
    const result = JSON.parse(response.text);
    validateResult(kind, result, summary);
    return { result, inputTokens: response.inputTokens, outputTokens: response.outputTokens, provider: attempt.provider, model: attempt.model };
  } finally {
    clearTimeout(timeout);
  }
}

export async function generateWithFallback(
  config: ProviderConfig,
  kind: "insight" | "workout",
  summary: Summary,
  fetchProvider: typeof fetch = fetch,
) {
  const attempts: ProviderAttempt[] = [
    ...config.openRouterModels.slice(0, 2).map((model) => ({ provider: "openrouter" as const, key: config.openRouterKey, model })),
    ...(config.geminiKey && config.geminiModel ? [{ provider: "gemini" as const, key: config.geminiKey, model: config.geminiModel }] : []),
  ].filter((attempt) => attempt.key.length > 0 && attempt.model.length > 0);
  let lastError: unknown;
  for (const attempt of attempts) {
    try {
      return await generateFromAttempt(attempt, kind, summary, fetchProvider);
    } catch (error) {
      lastError = error;
    }
  }
  if (lastError instanceof DOMException && lastError.name === "AbortError") throw lastError;
  throw new Error("AI providers unavailable");
}

/** One-provider adapter used by the credential-gated synthetic evaluation. */
export function generateWithProvider(
  provider: AiProvider,
  apiKey: string,
  model: string,
  kind: "insight" | "workout",
  summary: Summary,
  fetchProvider: typeof fetch = fetch,
) {
  return generateFromAttempt({ provider, key: apiKey, model }, kind, summary, fetchProvider);
}
