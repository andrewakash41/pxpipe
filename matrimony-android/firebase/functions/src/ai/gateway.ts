import { z } from "zod";
import { AnthropicProvider } from "./providers/anthropic.js";
import { GeminiProvider, MockProvider, OpenAiCompatibleProvider } from "./providers/others.js";
import { redactDeep } from "./redact.js";
import { AiError, type AiProvider, type AiTask } from "./types.js";

export interface ConsentChecker {
  /** True only if the member has opted in to the "AI matchmaking assistance" purpose. */
  hasAiConsent(uid: string): Promise<boolean>;
}

export interface QuotaStore {
  /** Atomically consumes one unit; returns false when the daily limit is reached. */
  consume(uid: string, task: string, dailyLimit: number): Promise<boolean>;
}

export class AiUnavailable extends Error {
  constructor(readonly reason: "no_consent" | "quota" | "provider" | "bad_output", message: string) {
    super(message);
    this.name = "AiUnavailable";
  }
}

export interface AiLogger {
  info(msg: string, data?: Record<string, unknown>): void;
  warn(msg: string, data?: Record<string, unknown>): void;
}

/**
 * The single entry point for every AI call. Order of checks:
 * consent → quota → render (de-identified + redacted + fenced) → provider → schema validation →
 * redaction of the output. Any failure raises [AiUnavailable] and the app falls back to its
 * deterministic templates, so AI is never on the critical path.
 */
export class AiGateway {
  constructor(
    private readonly provider: AiProvider,
    private readonly consent: ConsentChecker,
    private readonly quota: QuotaStore,
    private readonly log: AiLogger = { info: () => {}, warn: () => {} },
  ) {}

  get providerName(): string { return this.provider.name; }

  async run<I, O>(uid: string, task: AiTask<I, O>, input: I): Promise<O> {
    if (!(await this.consent.hasAiConsent(uid))) {
      throw new AiUnavailable("no_consent", "AI assistance is turned off for this member");
    }
    if (!(await this.quota.consume(uid, task.name, task.dailyLimit))) {
      throw new AiUnavailable("quota", "Daily AI limit reached for this feature");
    }
    const started = Date.now();
    let raw: unknown;
    try {
      raw = await this.provider.generateJson({
        task: task.name,
        system: task.system,
        user: task.render(input),
        jsonSchema: jsonSchemaFor(task.output),
        maxOutputTokens: task.maxOutputTokens,
      });
    } catch (e) {
      // Log the category only — never prompts or outputs, which may contain member data.
      this.log.warn("ai_provider_error", { task: task.name, provider: this.provider.name, code: e instanceof AiError ? e.code : "unknown" });
      throw new AiUnavailable("provider", "AI is unavailable right now");
    }
    const parsed = task.output.safeParse(raw);
    if (!parsed.success) {
      this.log.warn("ai_bad_output", { task: task.name, provider: this.provider.name });
      throw new AiUnavailable("bad_output", "AI returned an unexpected response");
    }
    this.log.info("ai_ok", { task: task.name, provider: this.provider.name, ms: Date.now() - started });
    return redactDeep(parsed.data);
  }
}

function jsonSchemaFor(schema: z.ZodType): Record<string, unknown> {
  const { $schema: _ignored, ...rest } = z.toJSONSchema(schema) as Record<string, unknown>;
  return rest;
}

export interface AiConfig {
  provider: string;
  model?: string;
  apiKey?: string;
  baseUrl?: string;
}

/** Builds the configured provider. Keys come from Secret Manager; nothing is hard-coded. */
export function createProvider(cfg: AiConfig): AiProvider {
  switch (cfg.provider) {
    case "anthropic":
      return new AnthropicProvider({ apiKey: cfg.apiKey, model: cfg.model });
    case "openai":
      return new OpenAiCompatibleProvider({ baseUrl: cfg.baseUrl || "https://api.openai.com/v1", apiKey: cfg.apiKey, model: cfg.model ?? "" });
    case "openai_compatible":
      if (!cfg.baseUrl) throw new AiError("AI_BASE_URL must be set for openai_compatible", "config");
      return new OpenAiCompatibleProvider({ baseUrl: cfg.baseUrl, apiKey: cfg.apiKey, model: cfg.model ?? "" });
    case "gemini":
      return new GeminiProvider({ apiKey: cfg.apiKey ?? "", model: cfg.model ?? "", baseUrl: cfg.baseUrl || undefined });
    case "mock":
      return new MockProvider();
    default:
      throw new AiError(`Unknown AI_PROVIDER "${cfg.provider}"`, "config");
  }
}
