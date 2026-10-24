import { defineConfig, loadEnv } from 'vite';

// The core's API key lives in the project's .env. The dev server adds it to proxied requests,
// so the browser never sees it and EventSource (which cannot send headers) still authenticates.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '');
  const apiKey = env.AGENT_OFFICE_API_KEY;
  const target = env.AGENT_OFFICE_URL ?? `http://localhost:${env.AGENT_OFFICE_PORT ?? '8080'}`;
  if (!apiKey) {
    console.warn('AGENT_OFFICE_API_KEY is not set in ../.env: the office will stay offline.');
  }
  return {
    // Phaser alone is over 1 MB minified; splitting it would not make the page load faster.
    build: { chunkSizeWarningLimit: 1500 },
    server: {
      port: Number(env.OFFICE_UI_PORT ?? 5173),
      proxy: {
        '/api': { target, changeOrigin: true, headers: apiKey ? { 'X-Api-Key': apiKey } : {} },
      },
    },
  };
});
