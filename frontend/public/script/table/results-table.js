import { HIDDEN_ID_KEY } from "../commons.js";
import { Render } from "./render.js";
import { TableEvents } from "./events.js";

// Results table itself: pinning, header reorder, column fit-to-width, and rendering.
export class ResultsTable {
  constructor(elements, { onHeaderReorder, onHeaderAutoFit, onRowCopy }) {
    this.elements = elements;
    this.onHeaderReorder = onHeaderReorder;
    this.onHeaderAutoFit = onHeaderAutoFit;
    this.onRowCopy = onRowCopy;

    // Caches the actual row data at pin time (keyed by id), so a pinned row survives even if a
    // later page fetch doesn't happen to include it.
    this.pinnedRows = new Map();
    this.currentData = [];
    this.currentHeaders = [];
    this.currentVisibleHeaders = new Set();
    this.currentContext = { term: null, page: null };

    this.idToRowMap = new Map();

    this.isMobile = window.matchMedia("(pointer: coarse)").matches || "ontouchstart" in window;

    if (!this.isMobile) this.initCopyContainer();

    this.eventHandler = new TableEvents(this);
    this.eventHandler.init();
  }

  getRowKey(row) {
    const idValue = row[HIDDEN_ID_KEY];
    // Builds a stable, URL/attribute-safe string key from the row's id, handling both
    // plain values and object-shaped ids (e.g. Mongo extended JSON).
    const idString = idValue !== null && typeof idValue === "object" ? JSON.stringify(idValue) : idValue;
    const key = `id:${encodeURIComponent(idString)}`;
    this.idToRowMap.set(key, row);
    return key;
  }

  getRowForCopy(rowId) {
    const row = this.idToRowMap.get(rowId);
    if (!row) return null;
    const { [HIDDEN_ID_KEY]: _id, ...rest } = row;
    return rest;
  }

  moveItem(array, fromIdx, toIdx) {
    if (fromIdx === -1 || toIdx === -1 || fromIdx === toIdx) return false;
    array.splice(toIdx, 0, array.splice(fromIdx, 1)[0]);
    return true;
  }

  reorderHeader(fromHeader, toHeader) {
    const headers = [...this.currentHeaders];
    if (this.moveItem(headers, headers.indexOf(fromHeader), headers.indexOf(toHeader))) this.onHeaderReorder(headers);
  }

  reorderPinnedRow(fromRowId, toRowId) {
    const pinnedArray = [...this.pinnedRows.entries()];
    const fromIdx = pinnedArray.findIndex(([key]) => key === fromRowId);
    const toIdx = pinnedArray.findIndex(([key]) => key === toRowId);

    if (!this.moveItem(pinnedArray, fromIdx, toIdx)) return;

    this.pinnedRows = new Map(pinnedArray);
    this.reorderDOMRows();
    Render.applyRowOffsets(this.elements.head, this.elements.body);
  }

  getPinnedRowsHeight() {
    const pinnedTrs = [...(this.elements.body?.querySelectorAll("tr.pinned") || [])];
    return pinnedTrs.reduce((sum, tr) => sum + tr.offsetHeight, 0);
  }

  getOrderedRows() {
    // Pins are global - always float, on every page, across every search term.
    // Nothing here ever removes an entry automatically
    const pinnedKeys = new Set(this.pinnedRows.keys());
    // Exclude any row from the page's own data that's already pinned, so it's never shown twice
    const unpinned = (this.currentData || []).filter((row) => !pinnedKeys.has(this.getRowKey(row)));
    const pinned = [...this.pinnedRows.values()].map((entry) => entry.row);
    return [...pinned, ...unpinned];
  }

  initCopyContainer() {
    const frame = this.elements.wrapper?.parentElement;
    if (!frame) return;

    this.copyContainer = frame.querySelector(".pin-copy-container");
    if (!this.copyContainer) {
      this.copyContainer = document.createElement("div");
      this.copyContainer.className = "pin-copy-container";
      frame.insertBefore(this.copyContainer, this.elements.wrapper);
    }

    if (!this.copyContainer.querySelector("#roaming-copy-btn")) {
      this.copyContainer.innerHTML = `
        <div class="pin-copy-item" id="roaming-copy-btn">
          <button type="button" class="btn-pin-copy" aria-label="Copy row">
            <span class="icon icon-lg icon-copy" aria-hidden="true"></span>
          </button>
        </div>
      `;
    }
    this.roamingBtn = this.copyContainer.querySelector("#roaming-copy-btn");
  }

  togglePinRow(tr) {
    if (!tr) return;
    const key = tr.dataset.rowId;
    const targetRow = this.idToRowMap.get(key);
    if (!targetRow) return;

    const isPinned = this.pinnedRows.has(key);

    // A stale click only needs blocking when it's neither on the page currently loaded
    // nor already a floated pin - i.e. it can't resolve to a real row either way.
    const isOnCurrentPage = this.currentData.some((row) => this.getRowKey(row) === key);
    if (!isOnCurrentPage && !isPinned) return;

    if (isPinned) {
      this.pinnedRows.delete(key);
    } else {
      const headerHeight = this.elements.head?.offsetHeight || 0;
      const currentPinnedHeight = this.getPinnedRowsHeight();
      const wrapperHeight = this.elements.wrapper?.clientHeight || 0;

      if (headerHeight + currentPinnedHeight + tr.offsetHeight >= wrapperHeight) return;

      // Map dedupes by id automatically, so re-pinning the same row can never create a duplicate entry.
      this.pinnedRows.set(key, {
        row: targetRow,
        term: this.currentContext.term,
        page: this.currentContext.page,
      });
    }

    // Re-render from the canonical current data rather than patching the existing DOM node -
    // otherwise an unpinned row keeps whatever stale content it had while it was floated, and
    // a re-pinned row can pick up whichever version idToRowMap happened to have most recently
    // (which may not match what was actually on screen).
    this.render(this.currentData, this.currentHeaders, this.currentVisibleHeaders);
  }

  reorderDOMRows() {
    const orderedRows = this.getOrderedRows();
    Render.syncDOMOrder(this.elements.body, orderedRows, (r) => this.getRowKey(r));
  }

  fitHeadersToWidth(headers, visibleHeaders) {
    const { wrapper } = this.elements;
    if (!wrapper || wrapper.clientWidth === 0) return;

    const visibleSet = new Set(visibleHeaders);
    const fullData = this.currentData;
    const calcData = fullData.slice(0, 5);

    this.render(calcData, headers, visibleSet);

    let changed = false;
    while (wrapper.clientWidth > 0 && wrapper.scrollWidth > wrapper.clientWidth && visibleSet.size > 1) {
      visibleSet.delete([...visibleSet].pop());
      this.render(calcData, headers, visibleSet);
      changed = true;
    }

    this.render(fullData, headers, visibleSet);
    if (changed) this.onHeaderAutoFit(visibleSet);
  }

  render(data = [], headers = [], visibleHeaders = new Set(), context = null) {
    // context is omitted for internal re-renders (e.g. fitHeadersToWidth) that don't
    // represent a new search - in that case just keep whatever context is already set.
    if (context) this.currentContext = context;

    this.currentData = data;
    this.currentHeaders = headers;
    this.currentVisibleHeaders = visibleHeaders;

    const visible = Render.getVisibleHeaders(headers, visibleHeaders);
    Render.renderHeaders(this.elements.head, visible);

    // Guard against missing body element
    if (!this.elements.body) return;

    if (data.length === 0) {
      const colSpan = Math.max(1, visible.length);
      this.elements.body.innerHTML = `<tr><td colspan="${colSpan}" class="no-results">No matching records found</td></tr>`;
      return;
    }

    const orderedRows = this.getOrderedRows();
    Render.renderBody(
      this.elements.body,
      orderedRows,
      visible,
      (r) => this.getRowKey(r),
      (key) => this.pinnedRows.has(key),
    );

    Render.applyRowOffsets(this.elements.head, this.elements.body);
    Render.markTruncatedCells(this.elements.body);
  }

  applyRowOffsets() {
    Render.applyRowOffsets(this.elements.head, this.elements.body);
  }

  markTruncatedCells() {
    Render.markTruncatedCells(this.elements.body);
  }
}
