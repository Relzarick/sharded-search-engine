// Values shared across app.js, filter.js, and results.js.
// Lives in its own module (not app.js) to avoid circular imports,
// since app.js imports from both filter.js and results.js.

export const HIDDEN_ID_KEY = "_id";

export const TABLE_WRAPPER_SELECTOR = ".table-wrapper";

export const SEARCH_ENDPOINT = "/search";

export const DEFAULT_PAGE_SIZE = 75;

export const humanizeHeader = (header) => header.replace(/_/g, " ");
