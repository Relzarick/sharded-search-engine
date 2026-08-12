import { DEFAULT_PAGE_SIZE, SEARCH_ENDPOINT } from "./script/commons.js";

// Sentinel queries for testing states without real data:
//   "0"  -> simulates a successful search with zero results
//   "9"  -> simulates the server erroring (500 response)
const DELAY_MS = 300;

function hashString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) hash = (hash << 5) - hash + str.charCodeAt(i);
  return Math.abs(hash);
}

function generateRow(index, query = "") {
  const num = index + 1;
  const prefix = query.trim() ? `${query.trim().toUpperCase()} — ` : "";

  return {
    // Mimics Mongo extended JSON (e.g. {$binary: {base64, subType: "04"}})
    _id: { $binary: { base64: btoa(`row-${100 + index}`), subType: "04" } },
    id: 100 + index,
    title: `${prefix}Project ${num}`,
    vote_average: Number((5 + (index % 50) / 10).toFixed(1)),
    vote_count: 100 + index * 15,
    status: `Status ${(index % 3) + 1}`,
    release_date: `2024-0${(index % 9) + 1}-15`,
    revenue: num * 1000000,
    runtime: 90 + (index % 60),
    budget: num * 500000,
    original_language: `Language ${(index % 5) + 1}`,
    director: `Director ${(index % 10) + 1}`,
    overview: `Overview details for project ${num}.`,
  };
}

function buildPayload(url) {
  const params = url.searchParams;
  const query = params.get("q") ?? "";

  let offset = parseInt(params.get("offset") ?? "0", 10);
  const limit = parseInt(params.get("size") ?? DEFAULT_PAGE_SIZE, 10);
  if (isNaN(offset) || offset < 0) offset = 0;

  let count = 0;
  let rows = [];

  const normalizedQuery = query.trim().toLowerCase();
  const isNoResults =
    normalizedQuery === "" || normalizedQuery === "0" || normalizedQuery === "none" || normalizedQuery === "empty";

  // Only generate rows if query is not flagged as empty
  if (!isNoResults) {
    const hash = hashString(query);
    const totalPages = 5 + (hash % 4);
    count = totalPages * limit;

    const rowCount = Math.min(limit, Math.max(0, count - offset));
    rows = Array.from({ length: rowCount }, (_, i) => generateRow(offset + i, query));
  }

  return { rows, count };
}

function mockResponse(request) {
  const query = new URL(request.url).searchParams.get("q") ?? "";
  const isServerError = query.trim() === "9";
  const payload = isServerError ? null : buildPayload(new URL(request.url));
  const signal = request.signal;

  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException("Aborted", "AbortError"));

    const timer = setTimeout(() => {
      if (isServerError) resolve(new Response("Internal Server Error", { status: 500 }));
      else
        resolve(
          new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } }),
        );
    }, DELAY_MS);

    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    });
  });
}

if (typeof window === "undefined") {
  // Running as the service worker itself
  self.addEventListener("install", () => self.skipWaiting());
  self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));
  self.addEventListener("fetch", (event) => {
    if (new URL(event.request.url).pathname === SEARCH_ENDPOINT) event.respondWith(mockResponse(event.request));
  });
} else if ("serviceWorker" in navigator) {
  // Running as a page script — register this same file as the service worker
  navigator.serviceWorker.register(import.meta.url, { type: "module" });
}
