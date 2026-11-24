// firebase-tools' HTTP client sends *every* request to HTTPS_PROXY when it is set and ignores
// NO_PROXY — including its own calls to the local emulators on 127.0.0.1. Behind a corporate or
// sandbox proxy that breaks emulator start-up. This preload keeps the proxy for all external
// traffic and only lets loopback requests go direct. It is a no-op when firebase-tools isn't
// resolvable (e.g. inside the functions runtime).
const path = require("node:path");
try {
  const toolsDir = path.dirname(require.resolve("firebase-tools/package.json", { paths: [process.cwd(), __dirname] }));
  const undici = require(require.resolve("undici", { paths: [toolsDir] }));
  const direct = new undici.Agent();
  const LOOPBACK = new Set(["127.0.0.1", "localhost", "::1", "[::1]"]);
  const Base = undici.ProxyAgent;
  class LoopbackDirectProxyAgent extends Base {
    dispatch(opts, handler) {
      const host = new URL(String(opts.origin)).hostname;
      return LOOPBACK.has(host) ? direct.dispatch(opts, handler) : super.dispatch(opts, handler);
    }
  }
  undici.ProxyAgent = LoopbackDirectProxyAgent;
} catch {
  // firebase-tools not present in this process; nothing to patch.
}
