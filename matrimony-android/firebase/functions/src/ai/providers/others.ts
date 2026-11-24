import { AiError, type AiProvider, type AiRequest } from "../types.js";

type Fetch = typeof fetch;

/**
 * Any OpenAI-compatible Chat Completions endpoint: OpenAI itself, Azure OpenAI-compatible
 * gateways, or self-hosted models served by vLLM, Ollama or LM Studio (keeps data in your VPC).
 */
export class OpenAiCompatibleProvider implements AiProvider {
  readonly name = "openai_compatible";
  constructor(private readonly opts: { baseUrl: string; apiKey?: string; model: string; fetch?: Fetch }) {
    if (!opts.model) throw new AiError("AI_MODEL must be set for the OpenAI-compatible provider", "config");
  }

  async generateJson(req: AiRequest): Promise<unknown> {
    const f = this.opts.fetch ?? fetch;
    const res = await f(`${this.opts.baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(this.opts.apiKey ? { authorization: `Bearer ${this.opts.apiKey}` } : {}),
      },
      body: JSON.stringify({
        model: this.opts.model,
        max_tokens: req.maxOutputTokens,
        messages: [
          { role: "system", content: req.system },
          { role: "user", content: req.user },
        ],
        response_format: { type: "json_schema", json_schema: { name: req.task, schema: req.jsonSchema, strict: true } },
      }),
      signal: AbortSignal.timeout(30_000),
    }).catch(() => { throw new AiError("OpenAI-compatible request failed", "provider"); });
    if (!res.ok) throw new AiError(`OpenAI-compatible API error ${res.status}`, "provider");
    const body = (await res.json()) as { choices?: { message?: { content?: string; refusal?: string }; finish_reason?: string }[] };
    const choice = body.choices?.[0];
    if (choice?.message?.refusal) throw new AiError("Model declined the request", "refusal");
    if (choice?.finish_reason === "length") throw new AiError("Output truncated", "truncated");
    try {
      return JSON.parse(choice?.message?.content ?? "");
    } catch {
      throw new AiError("Model returned non-JSON output", "bad_output");
    }
  }
}

/** Google Gemini via the Generative Language REST API with a JSON response schema. */
export class GeminiProvider implements AiProvider {
  readonly name = "gemini";
  constructor(private readonly opts: { apiKey: string; model: string; fetch?: Fetch; baseUrl?: string }) {
    if (!opts.model) throw new AiError("AI_MODEL must be set for the Gemini provider", "config");
  }

  async generateJson(req: AiRequest): Promise<unknown> {
    const f = this.opts.fetch ?? fetch;
    const base = this.opts.baseUrl ?? "https://generativelanguage.googleapis.com/v1beta";
    const res = await f(`${base}/models/${encodeURIComponent(this.opts.model)}:generateContent`, {
      method: "POST",
      // Key in a header, never in the URL (URLs end up in logs).
      headers: { "content-type": "application/json", "x-goog-api-key": this.opts.apiKey },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: req.system }] },
        contents: [{ role: "user", parts: [{ text: req.user }] }],
        generationConfig: { maxOutputTokens: req.maxOutputTokens, responseMimeType: "application/json", responseJsonSchema: req.jsonSchema },
      }),
      signal: AbortSignal.timeout(30_000),
    }).catch(() => { throw new AiError("Gemini request failed", "provider"); });
    if (!res.ok) throw new AiError(`Gemini API error ${res.status}`, "provider");
    const body = (await res.json()) as { candidates?: { content?: { parts?: { text?: string }[] }; finishReason?: string }[]; promptFeedback?: { blockReason?: string } };
    if (body.promptFeedback?.blockReason) throw new AiError("Model declined the request", "refusal");
    const c = body.candidates?.[0];
    if (c?.finishReason === "SAFETY") throw new AiError("Model declined the request", "refusal");
    if (c?.finishReason === "MAX_TOKENS") throw new AiError("Output truncated", "truncated");
    try {
      return JSON.parse(c?.content?.parts?.map((p) => p.text ?? "").join("") ?? "");
    } catch {
      throw new AiError("Model returned non-JSON output", "bad_output");
    }
  }
}

/** Deterministic provider for the emulator and tests; records requests so tests can inspect them. */
export class MockProvider implements AiProvider {
  readonly name = "mock";
  readonly requests: AiRequest[] = [];
  constructor(private readonly responder: (req: AiRequest) => unknown = defaultMockResponse) {}

  async generateJson(req: AiRequest): Promise<unknown> {
    this.requests.push(req);
    return this.responder(req);
  }
}

function defaultMockResponse(req: AiRequest): unknown {
  switch (req.task) {
    case "match_insight": return { summary: "You share important values and everyday habits.", talkingPoints: ["Talk about how you imagine family life."] };
    case "icebreakers": return { suggestions: ["What does a perfect Sunday look like for you?", "What's a place you'd love to visit together?", "What are you most proud of this year?"] };
    case "draft_bio": return { bio: "I enjoy simple things done well — good food, long walks and honest conversations." };
    case "kind_decline": return { message: "Thank you for your interest. I don't think we're the right match, but I wish you the very best." };
    case "translate": return { text: "(translated)" };
    case "assistant": return { reply: "Here are a few ideas to improve your matches." };
    case "parse_search": return {
      minAge: null, maxAge: null, cities: [], religions: [], motherTongues: [], diets: [], professions: [], minEducation: null,
      maritalStatuses: [], openToRelocate: null, livesAbroad: null, verifiedOnly: false, minHeightCm: null, keywords: [],
    };
    default: return {};
  }
}
