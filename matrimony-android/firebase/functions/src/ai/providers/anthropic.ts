import Anthropic from "@anthropic-ai/sdk";
import { jsonSchemaOutputFormat } from "@anthropic-ai/sdk/helpers/json-schema";
import { AiError, type AiProvider, type AiRequest } from "../types.js";

export const DEFAULT_ANTHROPIC_MODEL = "claude-opus-5-5";

/** Models that accept the server-side refusal fallback chain (`fallbacks: "default"`). */
const FALLBACK_CAPABLE = new Set(["claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5"]);

type BetaMessages = Pick<Anthropic["beta"]["messages"], "create">;

/**
 * Claude via the official Anthropic SDK.
 *  - Structured outputs (`output_config.format` with a JSON schema) so replies always parse.
 *  - Low effort: these are short, latency-sensitive generation tasks.
 *  - Server-side refusal fallback is enabled where the model supports it.
 */
export class AnthropicProvider implements AiProvider {
  readonly name = "anthropic";
  readonly model: string;
  private readonly messages: BetaMessages;

  constructor(opts: { apiKey?: string; model?: string; messages?: BetaMessages }) {
    this.model = opts.model || DEFAULT_ANTHROPIC_MODEL;
    this.messages = opts.messages ?? new Anthropic({ apiKey: opts.apiKey, maxRetries: 2, timeout: 30_000 }).beta.messages;
  }

  async generateJson(req: AiRequest): Promise<unknown> {
    const useFallback = FALLBACK_CAPABLE.has(this.model);
    // The SDK helper rewrites the schema into the subset structured outputs supports
    // (e.g. length/range constraints move into descriptions); the gateway still enforces them with zod.
    const schema = jsonSchemaOutputFormat(req.jsonSchema as { type: "object" }, { transform: true }).schema;
    let res;
    try {
      res = await this.messages.create({
        model: this.model,
        max_tokens: req.maxOutputTokens,
        ...(useFallback ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" as const } : {}),
        system: req.system,
        messages: [{ role: "user", content: req.user }],
        output_config: { effort: "low", format: { type: "json_schema", schema } },
      });
    } catch (e) {
      if (e instanceof Anthropic.RateLimitError) throw new AiError("Anthropic rate limit", "provider");
      if (e instanceof Anthropic.APIError) throw new AiError(`Anthropic API error ${e.status ?? ""}`.trim(), "provider");
      throw new AiError("Anthropic request failed", "provider");
    }
    if (res.stop_reason === "refusal") throw new AiError("Model declined the request", "refusal");
    if (res.stop_reason === "max_tokens") throw new AiError("Output truncated", "truncated");
    const text = res.content.flatMap((b) => (b.type === "text" ? [b.text] : [])).join("");
    try {
      return JSON.parse(text);
    } catch {
      throw new AiError("Model returned non-JSON output", "bad_output");
    }
  }
}
