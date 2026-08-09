export class MockSearchProvider {
  constructor({ delay = 300, pageSize = 75 } = {}) {
    this.delay = delay;
    this.pageSize = pageSize;
    this.originalFetch = window.fetch.bind(window);
    this.install();
  }

  install() {
    window.fetch = async (input, init) => {
      const urlStr = typeof input === "string" ? input : input?.url || "";
      if (urlStr.includes("/search")) return this.mockResponse(urlStr, init);
      return this.originalFetch(input, init);
    };
  }

  hashString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash << 5) - hash + str.charCodeAt(i);
    }
    return Math.abs(hash);
  }

  generateRow(index, query = "") {
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

  mockResponse(url, init = {}) {
    const queryString = url.includes("?") ? url.split("?")[1] : "";
    const params = new URLSearchParams(queryString);
    const query = params.get("q") ?? "";

    let offset = parseInt(params.get("offset") ?? "0", 10);
    const limit = parseInt(params.get("size") ?? this.pageSize, 10);
    if (isNaN(offset) || offset < 0) offset = 0;

    let count = 0;
    let rows = [];

    const normalizedQuery = query.trim().toLowerCase();
    const isNoResults =
      normalizedQuery === "" || normalizedQuery === "0" || normalizedQuery === "none" || normalizedQuery === "empty";

    // Only generate rows if query is not flagged as empty
    if (!isNoResults) {
      const hash = this.hashString(query);
      const totalPages = 5 + (hash % 4);
      count = totalPages * limit;

      const rowCount = Math.min(limit, Math.max(0, count - offset));
      rows = Array.from({ length: rowCount }, (_, i) => this.generateRow(offset + i, query));
    }

    const responsePayload = { rows, count };
    const signal = init?.signal;

    return new Promise((resolve, reject) => {
      if (signal?.aborted) return reject(new DOMException("Aborted", "AbortError"));

      const timer = setTimeout(() => {
        resolve(
          new Response(JSON.stringify(responsePayload), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }, this.delay);

      if (signal) {
        signal.addEventListener("abort", () => {
          clearTimeout(timer);
          reject(new DOMException("Aborted", "AbortError"));
        });
      }
    });
  }
}

new MockSearchProvider();
