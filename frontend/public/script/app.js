import { SearchBar } from "./search.js";
import { ResultsTable } from "./table/results-table.js";
import { ColumnFilter, ValueFilter } from "./filter.js";
import { Pagination } from "./pagination.js";
import { StatusNotifier, NotificationSource } from "./notifier.js";
import { HIDDEN_ID_KEY } from "./commons.js";

const DEFAULT_PAGE_SIZE = 75;

export class SearchService {
  constructor(baseUrl = "/search") {
    this.baseUrl = baseUrl;
    this.activeController = null;
  }

  async search(query, offset = 0, size = DEFAULT_PAGE_SIZE) {
    this.activeController?.abort();
    this.activeController = new AbortController();

    const params = new URLSearchParams({ q: query, offset, size });

    try {
      const res = await fetch(`${this.baseUrl}?${params}`, { signal: this.activeController.signal });
      if (!res.ok) throw new Error(`Search request failed with status: ${res.status}`);

      const json = await res.json();
      this.activeController = null;

      return {
        rows: json.rows || [],
        count: json.count,
      };
    } catch (err) {
      if (err.name === "AbortError") return { aborted: true, rows: [], count: 0 };
      this.activeController = null;
      throw err;
    }
  }
}

const $ = (selector) => document.querySelector(selector);

const setVisibility = (el, isVisible) => {
  if (el) el.hidden = !isVisible;
};

const isDateString = (val) =>
  typeof val === "string" && isNaN(Number(val)) && !isNaN(Date.parse(val)) && (val.includes("-") || val.includes("/"));

export class SearchResultsView {
  constructor({
    onSearch,
    onHeaderToggle,
    onToggleAllHeaders,
    onValueFilterChange,
    onHeaderReorder,
    onHeaderAutoFit,
    onPageChange,
  }) {
    this.container = $("[data-table-container]");
    this.tableFrame = $("[data-table-frame]");
    this.paginationContainer = $(".pagination-container");
    this.fullscreenBtn = $(".btn-fullscreen");
    this.headerNotice = $(".header-notice");

    this.notifier = new StatusNotifier($("[data-status-message]"));

    this.searchBar = new SearchBar($(".search-form"), $('input[name="query"]'), (query) => onSearch(query));

    this.grid = new ResultsTable(
      {
        wrapper: $(".table-wrapper"),
        head: $("[data-table-head]"),
        body: $("[data-table-body]"),
      },
      {
        onHeaderReorder,
        onHeaderAutoFit,
        onRowCopy: (row) => {
          navigator.clipboard.writeText(JSON.stringify(row, null, 2));
          this.notifier.notify(NotificationSource.ROW_COPY);
        },
      },
    );

    this.columnFilter = new ColumnFilter(
      {
        btn: $(".btn-filter"),
        menu: $(".filter-menu"),
        selectAll: $(".filter-select-all input"),
        options: $(".filter-options"),
        count: $(".filter-count"),
        alignTo: $(".table-frame"),
      },
      {
        onToggle: onHeaderToggle,
        onToggleAll: onToggleAllHeaders,
      },
    );

    this.valueFilter = new ValueFilter({
      btn: $('[popovertarget="value-menu"]'),
      menu: $(".value-menu"),
      alignTo: $(".table-frame"),
    });

    this.valueFilter.elements.menu?.addEventListener("value-filter-change", (e) => {
      onValueFilterChange(e.detail.header, e.detail.filter);
    });

    this.pagination = new Pagination(this.paginationContainer, (page) => onPageChange(page));

    if (this.fullscreenBtn) this.fullscreenBtn.addEventListener("click", () => this.toggleFullscreen());
  }

  notifySearchResults(count) {
    if (count === 0) return this.notifier.notify(NotificationSource.NO_RESULTS);
    this.notifier.notify(NotificationSource.SEARCH_RESULTS, count);
  }

  toggleFullscreen() {
    if (!this.container) return;
    const isFullscreen = this.container.classList.toggle("is-fullscreen");
    if (this.fullscreenBtn) this.fullscreenBtn.setAttribute("aria-pressed", String(isFullscreen));
    document.body.classList.toggle("no-scroll", isFullscreen);

    requestAnimationFrame(() => {
      this.grid.applyRowOffsets();
      this.grid.markTruncatedCells();
    });
  }

  setLoading(isLoading) {
    this.searchBar.setLoading(isLoading);
  }

  hideHeaderNotice() {
    this.headerNotice?.classList.add("is-hidden");
  }

  setSchema(schema) {
    this.valueFilter.schema = schema;
  }

  setResultsVisibility(isVisible) {
    setVisibility(this.container, isVisible);
  }

  updateAutoFitColumns(headers, visibleHeaders) {
    this.columnFilter.render(headers, visibleHeaders);
  }

  fitHeadersToWidth(headers, visibleHeaders) {
    this.grid.fitHeadersToWidth(headers, visibleHeaders);
  }

  renderGrid(state) {
    const hasColumns = state.visibleHeaders.size > 0;
    if (!hasColumns) return;

    const filteredData = this.valueFilter.filterData(state.data);
    this.grid.render(filteredData, state.headers, state.visibleHeaders, { term: state.lastQuery, page: state.page });
  }

  finishRender(state) {
    const hasDataAndColumns = state.visibleHeaders.size > 0 && state.data.length > 0;

    setVisibility(this.tableFrame, hasDataAndColumns);
    setVisibility(this.paginationContainer, hasDataAndColumns);

    this.columnFilter.render(state.headers, state.visibleHeaders);
    this.valueFilter.render(state.headers);
    this.pagination.render(state.page + 1, state.totalPages);

    if (hasDataAndColumns) this.renderGrid(state);
  }
}

const createInitialState = () => ({
  data: [],
  headers: [],
  visibleHeaders: new Set(),
  lastQuery: null,
  page: 0,
  pageSize: DEFAULT_PAGE_SIZE,
  totalPages: null,
});

export class SearchApp {
  constructor(searchService = new SearchService(), view = null) {
    this.searchService = searchService;
    this.state = createInitialState();

    this.view =
      view ||
      new SearchResultsView({
        onSearch: (query) => this.handleSearch(query),
        onHeaderToggle: (header, checked) => this.handleHeaderToggle(header, checked),
        onToggleAllHeaders: (checked) => this.handleToggleAllHeaders(checked),
        onValueFilterChange: (header, filter) => this.handleValueFilterChange(header, filter),
        onHeaderReorder: (headers) => this.handleHeaderReorder(headers),
        onHeaderAutoFit: (visibleSet) => this.handleHeaderAutoFit(visibleSet),
        onPageChange: (page) => this.handlePageChange(page),
      });
  }

  async handleSearch(query) {
    this.view.hideHeaderNotice();
    this.state.lastQuery = query;
    this.state.page = 0;
    await this.fetchData();
  }

  async handlePageChange(uiPage) {
    this.state.page = uiPage - 1;
    await this.fetchData();
  }

  handleHeaderToggle(header, isChecked) {
    if (isChecked) this.state.visibleHeaders.add(header);
    else this.state.visibleHeaders.delete(header);
    this.view.finishRender(this.state);
  }

  handleToggleAllHeaders(isChecked) {
    this.state.visibleHeaders = isChecked ? new Set(this.state.headers) : new Set();
    this.view.finishRender(this.state);
  }

  handleValueFilterChange() {
    this.view.renderGrid(this.state);
  }

  handleHeaderReorder(newHeaders) {
    this.state.headers = newHeaders;
    this.view.finishRender(this.state);
  }

  handleHeaderAutoFit(updatedVisibleSet) {
    this.state.visibleHeaders = updatedVisibleSet;
    this.view.updateAutoFitColumns(this.state.headers, this.state.visibleHeaders);
  }

  async fetchData() {
    if (this.state.lastQuery === null) return;

    this.view.setLoading(true);

    try {
      const offset = this.state.page * this.state.pageSize;
      const response = await this.searchService.search(this.state.lastQuery, offset, this.state.pageSize);
      if (!response.aborted) this.processSearchResults(response);
    } catch (error) {
      console.error("Search failed:", error);
    } finally {
      this.view.setLoading(false);
    }
  }

  detectSchema(headers, sampleRow) {
    if (!sampleRow) return new Map();
    const schema = new Map();

    headers.forEach((header) => {
      const val = sampleRow[header];
      let type = "string";

      if (typeof val === "number") type = "number";
      else if (val instanceof Date || isDateString(val)) type = "date";

      schema.set(header, type);
    });

    return schema;
  }

  processSearchResults({ rows, count }) {
    this.state.data = rows;
    const totalCount = typeof count === "number" ? count : rows.length;

    if (typeof count === "number") {
      this.state.totalPages = Math.ceil(count / this.state.pageSize);
    }

    // Keep table-container visible so status notifier displays inside toolbar
    this.view.setResultsVisibility(true);

    // Only notify search results on initial search (page 0 / offset 0)
    if (this.state.page === 0) {
      this.view.notifySearchResults(totalCount);
    }

    const hasData = Boolean(this.state.data.length);

    if (!hasData) {
      this.view.finishRender(this.state);
      return;
    }

    const incomingHeaders = Object.keys(rows[0]).filter((k) => k !== HIDDEN_ID_KEY);

    if (!this.state.headers.length) {
      this.state.headers = incomingHeaders;
      this.state.visibleHeaders = new Set(incomingHeaders);
    } else {
      const preserved = this.state.headers.filter((h) => incomingHeaders.includes(h));
      const newHeaders = incomingHeaders.filter((h) => !this.state.headers.includes(h));

      this.state.headers = [...preserved, ...newHeaders];
      newHeaders.forEach((h) => this.state.visibleHeaders.add(h));
    }

    this.view.setSchema(this.detectSchema(this.state.headers, rows[0]));

    this.view.finishRender(this.state);
    this.view.fitHeadersToWidth(this.state.headers, this.state.visibleHeaders);
  }
}

const startApp = () => new SearchApp();

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => startApp());
else startApp();
