import { Effect } from "effect";

export type GrainReply = Record<string, unknown>;

export class GrainSessionClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token: string,
    readonly owner: string,
  ) {
    const url = new URL(baseUrl);
    if (url.protocol !== "http:" || !["127.0.0.1", "localhost"].includes(url.hostname)) {
      throw new Error("Grain session endpoint must be loopback HTTP");
    }
    if (!token || !owner) throw new Error("Grain session credential and owner are required");
  }

  callEffect(operation: string, data: GrainReply = {}) {
    const baseUrl = this.baseUrl;
    const token = this.token;
    const owner = this.owner;
    return Effect.gen(function* () {
      const response = yield* Effect.tryPromise(() =>
        fetch(`${baseUrl.replace(/\/$/u, "")}/internal/session/${operation}`, {
          method: "POST",
          headers: {
            authorization: `Bearer ${token}`,
            "x-awp-owner": owner,
            "content-type": "application/json",
          },
          body: JSON.stringify(data),
          signal: AbortSignal.timeout(10_000),
        }));
      const result = yield* Effect.tryPromise(() => response.json() as Promise<GrainReply>);
      if (!response.ok || result.error) {
        return yield* Effect.fail(new Error(
          typeof result.error === "string" ? result.error : "Grain session request failed"));
      }
      return result;
    });
  }

  call(operation: string, data: GrainReply = {}): Promise<GrainReply> {
    return Effect.runPromise(this.callEffect(operation, data));
  }
}
