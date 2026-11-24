import type { z } from "zod";

/** One structured-output generation. `user` must already be de-identified and redacted. */
export interface AiRequest {
  task: string;
  system: string;
  user: string;
  /** JSON Schema for the expected output (derived from a zod schema by the gateway). */
  jsonSchema: Record<string, unknown>;
  maxOutputTokens: number;
}

/**
 * The only thing a provider must do: return JSON (as an unknown value) for a request.
 * Validation against the zod schema happens in the gateway, so every provider is held to the
 * same contract and a misbehaving model can never push unvalidated data to clients.
 */
export interface AiProvider {
  readonly name: string;
  generateJson(req: AiRequest): Promise<unknown>;
}

export class AiError extends Error {
  constructor(message: string, readonly code: "refusal" | "truncated" | "bad_output" | "provider" | "config") {
    super(message);
    this.name = "AiError";
  }
}

/** A task: its prompt, its input type, and the schema its output must satisfy. */
export interface AiTask<I, O> {
  name: string;
  dailyLimit: number;
  maxOutputTokens: number;
  output: z.ZodType<O>;
  system: string;
  render(input: I): string;
}
