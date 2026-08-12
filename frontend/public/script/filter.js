import { humanizeHeader, TABLE_WRAPPER_SELECTOR } from "./commons.js";

const escapeAttr = (val) => String(val ?? "").replace(/"/g, "&quot;");

export class BaseFilter {
  constructor(elements) {
    this.elements = elements;
    this.repositionHandler = null;
    this.resizeObserver = null;
  }

  initToggleListener(onOpen, onClose) {
    this.elements.menu?.addEventListener("toggle", (e) => {
      const isOpen = e.newState === "open";
      this.elements.btn?.setAttribute("aria-expanded", String(isOpen));

      if (isOpen) {
        this.startPositioning();
        onOpen?.(e);
      } else {
        this.stopPositioning();
        onClose?.(e);
      }
    });
  }

  startPositioning() {
    this.repositionHandler = () => this.positionMenu();
    window.addEventListener("resize", this.repositionHandler);
    window.addEventListener("scroll", this.repositionHandler, true);

    if (window.ResizeObserver && this.elements.alignTo) {
      this.resizeObserver = new ResizeObserver(this.repositionHandler);
      this.resizeObserver.observe(this.elements.alignTo);
    }

    this.positionMenu();
  }

  stopPositioning() {
    if (this.repositionHandler) {
      window.removeEventListener("resize", this.repositionHandler);
      window.removeEventListener("scroll", this.repositionHandler, true);
      this.repositionHandler = null;
    }
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    this.elements.menu?.classList.remove("is-positioned");
  }

  positionMenu() {
    if (!this.elements.menu || !this.elements.btn) return;

    const viewportWidth = document.documentElement.clientWidth;

    const hasAlignTo = Boolean(this.elements.alignTo?.getClientRects().length);
    const alignEl = hasAlignTo ? this.elements.alignTo : this.elements.btn;
    const alignRect = alignEl.getBoundingClientRect();

    const marginTop = 0;
    const top = alignRect.top + marginTop;

    const scrollEl = alignEl.querySelector(TABLE_WRAPPER_SELECTOR) || alignEl;
    let scrollbarWidth = 0;

    if (scrollEl) {
      const style = window.getComputedStyle(scrollEl);
      const borderLeft = parseFloat(style.borderLeftWidth) || 0;
      const borderRight = parseFloat(style.borderRightWidth) || 0;
      const widthDiff = scrollEl.offsetWidth - scrollEl.clientWidth - borderLeft - borderRight;
      scrollbarWidth = Math.max(0, widthDiff);
    }

    const baseRight = Math.max(0, viewportWidth - alignRect.right);
    const right = baseRight + scrollbarWidth;

    this.elements.menu.classList.add("is-positioned");
    Object.assign(this.elements.menu.style, {
      top: `${top}px`,
      right: `${right}px`,
    });
  }
}

export class ColumnFilter extends BaseFilter {
  constructor(elements, { onToggle, onToggleAll } = {}) {
    super(elements);
    this.onToggle = onToggle;
    this.onToggleAll = onToggleAll;
    this.searchInput = this.elements.menu?.querySelector(".filter-search-input");
    this.initEventListeners();
  }

  initEventListeners() {
    this.initToggleListener(null, () => {
      this.resetSearchFilter();
    });

    const handleInput = () => this.filterOptionsInDom();
    this.searchInput?.addEventListener("input", handleInput);
    this.searchInput?.addEventListener("search", handleInput);

    this.elements.selectAll?.addEventListener("change", (e) => {
      this.onToggleAll?.(e.target.checked);
    });

    this.elements.options?.addEventListener("change", (e) => {
      const checkbox = e.target.closest('input[type="checkbox"]');
      if (checkbox?.dataset.header) this.onToggle?.(checkbox.dataset.header, checkbox.checked);
    });
  }

  render(headers = [], visibleHeaders = new Set()) {
    if (this.elements.options) {
      this.elements.options.innerHTML = headers
        .map(
          (header) => `
        <label class="filter-label">
          <input type="checkbox" data-header="${header}" ${visibleHeaders.has(header) ? "checked" : ""}>
          <span>${humanizeHeader(header)}</span>
        </label>`,
        )
        .join("");
    }

    if (this.elements.selectAll)
      this.elements.selectAll.checked = headers.length > 0 && visibleHeaders.size === headers.length;

    if (this.elements.count) this.elements.count.textContent = `${visibleHeaders.size} of ${headers.length} columns`;

    if (this.searchInput?.value) this.filterOptionsInDom();
  }

  filterOptionsInDom() {
    if (!this.searchInput || !this.elements.options) return;
    const query = this.searchInput.value.trim().toLowerCase();

    this.elements.options.querySelectorAll(".filter-label").forEach((label) => {
      label.hidden = !label.textContent.toLowerCase().includes(query);
    });
  }

  resetSearchFilter() {
    if (!this.searchInput) return;
    this.searchInput.value = "";
    this.filterOptionsInDom();
  }
}

const VALUE_INPUT_DEBOUNCE_MS = 200;

export class ValueFilter extends BaseFilter {
  constructor(elements) {
    super(elements);
    this.schema = new Map();
    this.filters = new Map();
    this.activeHeaders = new Set();
    this.headers = [];
    this.container = null;
    this.debounceTimers = new Map();
    this.initEventListeners();
  }

  initEventListeners() {
    this.initToggleListener(null, () => this.resetSearchFilter());

    this.elements.menu?.addEventListener("click", (e) => this.handleMenuClick(e));

    this.elements.menu?.addEventListener("change", (e) => {
      const operator = e.target.closest(".value-field-operator");
      if (operator) return this.handleOperatorChange(operator.dataset.header, operator.value);

      const input = e.target.closest(".value-field-input");
      if (input) this.handleInputChange(input.dataset.header, input.dataset.role, input.value);
    });

    this.elements.menu?.addEventListener("input", (e) => {
      const searchInput = e.target.closest(".filter-search-input");
      if (searchInput) return this.handleSearchInput(searchInput.value);

      const input = e.target.closest(".value-field-input");
      if (input) this.handleInputChange(input.dataset.header, input.dataset.role, input.value);
    });
  }

  resetSearchFilter() {
    const searchInput = this.elements.menu?.querySelector(".filter-search-input");
    if (!searchInput) return;
    searchInput.value = "";
    this.handleSearchInput("");
  }

  dispatchFilterChange(header, filter) {
    this.elements.menu?.dispatchEvent(
      new CustomEvent("value-filter-change", { detail: { header, filter }, bubbles: true }),
    );
  }

  dispatchFilterChangeDebounced(header, filter) {
    clearTimeout(this.debounceTimers.get(header));
    const timer = setTimeout(() => {
      this.dispatchFilterChange(header, filter);
      this.debounceTimers.delete(header);
    }, VALUE_INPUT_DEBOUNCE_MS);
    this.debounceTimers.set(header, timer);
  }

  createDefaultFilter(type) {
    return {
      mode: type === "string" ? "contains" : "eq",
      value: type === "string" ? "" : null,
      min: null,
      max: null,
    };
  }

  handleSearchInput(term) {
    if (!this.container) return;
    const lowerTerm = term.toLowerCase();

    for (const child of this.container.children) {
      const header = child.dataset.header || "";
      const displayString = humanizeHeader(header).toLowerCase();
      child.hidden = !displayString.includes(lowerTerm);
    }
  }

  handleMenuClick(e) {
    const refreshBtn = e.target.closest(".value-refresh-btn");
    if (refreshBtn) {
      e.stopPropagation();
      return this.handleResetHeader(refreshBtn.dataset.header);
    }

    const chip = e.target.closest(".value-chip");
    if (!chip) return;

    const header = chip.dataset.header;
    const isActive = !this.activeHeaders.has(header);
    const type = this.schema.get(header) || "string";

    isActive ? this.activeHeaders.add(header) : this.activeHeaders.delete(header);

    if (isActive) this.filters.set(header, this.createDefaultFilter(type));
    else this.filters.delete(header);

    this.dispatchFilterChange(header, this.filters.get(header) || null);
    this.render();
  }

  handleResetHeader(header) {
    const type = this.schema.get(header) || "string";
    if (!this.filters.has(header)) return;

    this.filters.set(header, this.createDefaultFilter(type));
    this.dispatchFilterChange(header, this.filters.get(header));
    this.render();
  }

  handleOperatorChange(header, mode) {
    const prev = this.filters.get(header) || {};
    const filter = { mode, value: prev.value ?? "", min: prev.min ?? null, max: prev.max ?? null };

    this.filters.set(header, filter);
    this.dispatchFilterChange(header, filter);
    this.render();
  }

  handleInputChange(header, role, rawValue) {
    const filter = this.filters.get(header);
    if (!filter) return;

    const type = this.schema.get(header);
    if (rawValue === "") filter[role] = type === "string" ? "" : null;
    else filter[role] = type === "string" || type === "date" ? rawValue : Number(rawValue);

    this.dispatchFilterChangeDebounced(header, filter);
  }

  buildInputsHtml(header, filter, type) {
    if (!filter) return "";
    const safeHeader = escapeAttr(header);

    if (type === "string") {
      return `<input type="text" class="value-field-input" data-header="${safeHeader}" data-role="value" value="${escapeAttr(filter.value)}">`;
    }

    const inputType = type === "date" ? "date" : "number";
    const stepAttr = type === "number" ? ' step="any"' : "";

    if (filter.mode === "range") {
      return `
        <input type="${inputType}"${stepAttr} class="value-field-input" data-header="${safeHeader}" data-role="min" value="${escapeAttr(filter.min)}">
        <input type="${inputType}"${stepAttr} class="value-field-input" data-header="${safeHeader}" data-role="max" value="${escapeAttr(filter.max)}">`;
    }

    return `<input type="${inputType}"${stepAttr} class="value-field-input" data-header="${safeHeader}" data-role="value" value="${escapeAttr(filter.value)}">`;
  }

  buildTypedFieldHtml(header, isActive, type) {
    const filter = this.filters.get(header);
    const safeHeader = escapeAttr(header);

    const operatorOptions =
      type === "string"
        ? `
          <option value="contains" ${filter?.mode === "contains" ? "selected" : ""}>∈</option>
          <option value="exclude" ${filter?.mode === "exclude" ? "selected" : ""}>∉</option>`
        : `
          <option value="eq" ${filter?.mode === "eq" ? "selected" : ""}>=</option>
          <option value="lt" ${filter?.mode === "lt" ? "selected" : ""}>&lt;</option>
          <option value="gt" ${filter?.mode === "gt" ? "selected" : ""}>&gt;</option>
          <option value="range" ${filter?.mode === "range" ? "selected" : ""}>~</option>`;

    const childHtml = isActive
      ? `
        <div class="value-field-child" data-header="${safeHeader}">
          <select class="value-field-operator" data-header="${safeHeader}">${operatorOptions}</select>
          <div class="value-field-inputs" data-header="${safeHeader}">${this.buildInputsHtml(header, filter, type)}</div>
        </div>`
      : "";

    const refreshBtnHtml = isActive
      ? `
        <button type="button" class="value-refresh-btn" data-header="${safeHeader}" title="Reset field">
          <span class="icon icon-refresh"></span>
        </button>`
      : "";

    return `
      <div class="value-field${isActive ? " is-active" : ""}" data-header="${safeHeader}">
        <div class="value-field-header">
          <button type="button" class="value-chip" data-header="${safeHeader}">
            <span class="value-chip-label">
              <span>${humanizeHeader(header)}</span>
              <span class="icon icon-chevron-down"></span>
            </span>
          </button>
          ${refreshBtnHtml}
        </div>
        ${childHtml}
      </div>`;
  }

  initContainer() {
    this.elements.menu.innerHTML = `
      <div class="filter-search">
        <input type="text" class="filter-search-input" placeholder="search..." aria-label="Search values">
      </div>
      <div class="value-fields-container"></div>
    `;
    this.container = this.elements.menu.querySelector(".value-fields-container");
  }

  render(headers) {
    if (headers) this.headers = headers;
    if (!this.container) this.initContainer();

    this.container.innerHTML = this.headers
      .map((header) => {
        const type = this.schema.get(header) || "string";
        const isActive = this.activeHeaders.has(header);
        return this.buildTypedFieldHtml(header, isActive, type);
      })
      .join("");

    const currentSearch = this.elements.menu.querySelector(".filter-search-input")?.value;
    if (currentSearch) this.handleSearchInput(currentSearch);
  }

  filterData(data = []) {
    if (!this.filters.size) return data;
    return data.filter((row) => this.matchesRow(row));
  }

  matchesRow(row) {
    if (!row) return false;
    for (const [header, filter] of this.filters) {
      const type = this.schema.get(header) || "string";
      const val = row[header];

      if (!this.evaluateValue(val, filter, type)) {
        return false;
      }
    }
    return true;
  }

  evaluateValue(val, filter, type) {
    if (type === "number") return this.evaluateNumber(val, filter);
    if (type === "date") return this.evaluateDate(val, filter);
    return this.evaluateString(val, filter);
  }

  evaluateString(val, filter) {
    const target = (filter.value ?? "").toString().trim().toLowerCase();
    if (!target) return true;

    const cellVal = (val ?? "").toString().toLowerCase();
    if (filter.mode === "exclude") {
      return !cellVal.includes(target);
    }
    return cellVal.includes(target);
  }

  evaluateNumber(val, filter) {
    return this.evaluateComparable(val, filter, {
      toComparable: Number,
      hasValue: (v) => v !== null && v !== "",
      isValid: (v) => v !== null && v !== "" && !isNaN(Number(v)),
      equals: (a, b) => Number(a) === Number(b),
    });
  }

  evaluateDate(val, filter) {
    return this.evaluateComparable(val, filter, {
      toComparable: (v) => new Date(v).getTime(),
      hasValue: (v) => Boolean(v),
      isValid: (v) => Boolean(v) && !isNaN(new Date(v).getTime()),
      // Dates compare by day, not exact timestamp - two times on the same day should match.
      equals: (a, b) => new Date(a).toISOString().slice(0, 10) === new Date(b).toISOString().slice(0, 10),
    });
  }

  // Shared range/single-value comparison logic for any type that can be reduced to a
  // comparable number (via toComparable). Callers supply the type-specific rules.
  evaluateComparable(val, filter, { toComparable, hasValue, isValid, equals }) {
    const valIsValid = isValid(val);
    const comparableVal = valIsValid ? toComparable(val) : null;

    if (filter.mode === "range") {
      const minHasVal = hasValue(filter.min);
      const maxHasVal = hasValue(filter.max);
      if (!minHasVal && !maxHasVal) return true;

      if (!valIsValid) return false;
      if (minHasVal && comparableVal < toComparable(filter.min)) return false;
      if (maxHasVal && comparableVal > toComparable(filter.max)) return false;
      return true;
    }

    if (!hasValue(filter.value)) return true;
    if (!valIsValid) return false;

    if (filter.mode === "lt") return comparableVal < toComparable(filter.value);
    if (filter.mode === "gt") return comparableVal > toComparable(filter.value);
    return equals(val, filter.value);
  }
}
