import { assertEquals, assertRejects } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { generateWithFallback, generateWithProvider } from "./provider.ts";

const summary = { periodDays: 28, activityCount: 2, distanceMeters: 4_200, movingSeconds: 2_700, sports: ["RUNNING"], focus: "consistency" as const };
const result = {
  title: "Steady routine",
  summary: "2 activities in 28 days covered 4.2 km.",
  observations: ["The available period contains 2 activities."],
  suggestedAction: "Try another relaxed session when it fits your week.",
};

Deno.test("OpenRouter adapter sends a strict schema and validates its response", async () => {
  const captured: { url: string; headers?: Headers; body: Record<string, unknown> } = { url: "", body: {} };
  const fakeFetch: typeof fetch = (input, init) => {
    captured.url = String(input);
    captured.headers = new Headers(init?.headers);
    captured.body = JSON.parse(String(init?.body));
    return Promise.resolve(new Response(JSON.stringify({
      choices: [{ finish_reason: "stop", message: { content: JSON.stringify(result) } }],
      usage: { prompt_tokens: 110, completion_tokens: 55 },
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
  };

  const generated = await generateWithProvider("openrouter", "synthetic-test-key", "openrouter/free", "insight", summary, fakeFetch);
  assertEquals(captured.url, "https://openrouter.ai/api/v1/chat/completions");
  assertEquals(captured.headers?.get("Authorization"), "Bearer synthetic-test-key");
  assertEquals(captured.body.model, "openrouter/free");
  assertEquals((captured.body.response_format as { type: string }).type, "json_schema");
  assertEquals((captured.body.provider as { data_collection: string }).data_collection, "deny");
  assertEquals(generated.result, result);
  assertEquals(generated.inputTokens, 110);
  assertEquals(generated.outputTokens, 55);
  assertEquals(generated.provider, "openrouter");
});

Deno.test("fallback tries Gemini after OpenRouter fails and keeps its key out of the URL", async () => {
  const requests: Array<{ url: string; headers: Headers; body: Record<string, unknown> }> = [];
  const fakeFetch: typeof fetch = (input, init) => {
    const url = String(input);
    const headers = new Headers(init?.headers);
    const body = JSON.parse(String(init?.body));
    requests.push({ url, headers, body });
    if (url.includes("openrouter.ai")) return Promise.resolve(new Response("unavailable", { status: 503 }));
    return Promise.resolve(new Response(JSON.stringify({
      candidates: [{ finishReason: "STOP", content: { parts: [{ text: JSON.stringify(result) }] } }],
      usageMetadata: { promptTokenCount: 120, candidatesTokenCount: 60 },
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
  };
  const generated = await generateWithFallback({
    openRouterKey: "router-test-key", openRouterModels: ["openrouter/free"],
    geminiKey: "gemini-test-key", geminiModel: "gemini-test-model",
  }, "insight", summary, fakeFetch);
  assertEquals(requests.length, 2);
  assertEquals(requests[1].url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent");
  assertEquals(requests[1].headers.get("x-goog-api-key"), "gemini-test-key");
  assertEquals(requests[1].url.includes("gemini-test-key"), false);
  assertEquals((requests[1].body.generationConfig as { responseMimeType: string }).responseMimeType, "application/json");
  assertEquals(generated.provider, "gemini");
  assertEquals(generated.inputTokens, 120);
  assertEquals(generated.outputTokens, 60);
});

Deno.test("fallback advances after a schema or policy rejection", async () => {
  let call = 0;
  const fakeFetch: typeof fetch = () => {
    call += 1;
    const output = call === 1 ? { ...result, summary: "Unsupported 99 km claim." } : result;
    return Promise.resolve(new Response(JSON.stringify({
      choices: [{ finish_reason: "stop", message: { content: JSON.stringify(output) } }],
      usage: { prompt_tokens: 10, completion_tokens: 10 },
    }), { status: 200 }));
  };
  const generated = await generateWithFallback({
    openRouterKey: "router-test-key", openRouterModels: ["model-a:free", "model-b:free"],
    geminiKey: "", geminiModel: "",
  }, "insight", summary, fakeFetch);
  assertEquals(call, 2);
  assertEquals(generated.result, result);
});

Deno.test("fallback has a finite attempt count and fails closed without providers", async () => {
  let call = 0;
  const fakeFetch: typeof fetch = () => { call += 1; return Promise.resolve(new Response("unavailable", { status: 503 })); };
  await assertRejects(() => generateWithFallback({
    openRouterKey: "router-test-key", openRouterModels: ["a:free", "b:free", "c:free", "d:free"],
    geminiKey: "gemini-test-key", geminiModel: "gemini-test-model",
  }, "insight", summary, fakeFetch), Error, "AI providers unavailable");
  assertEquals(call, 3);
  await assertRejects(() => generateWithFallback({ openRouterKey: "", openRouterModels: [], geminiKey: "", geminiModel: "" }, "insight", summary, fakeFetch), Error);
});
