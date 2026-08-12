import { humanizeHeader } from "../commons.js";

// Static rendering helpers for the results table (headers, rows, sticky offsets, truncation).
export class Render {
  static getVisibleHeaders(headers, visibleHeaders) {
    return headers.filter((h) => visibleHeaders.has(h));
  }

  static renderHeaders(headEl, visibleHeaders) {
    if (!headEl) return;
    headEl.innerHTML = `<tr>${visibleHeaders
      .map((h) => `<th draggable="true" data-header="${h}" class="draggable-handle">${humanizeHeader(h)}</th>`)
      .join("")}</tr>`;
  }

  static renderBody(bodyEl, orderedRows, visibleHeaders, getRowKey, isPinnedKey) {
    if (!bodyEl) return;
    bodyEl.innerHTML = orderedRows
      .map((row) => {
        const rowId = getRowKey(row);
        const isPinned = isPinnedKey(rowId);
        const cellsHtml = visibleHeaders
          .map((h, i) => {
            const isHandle = isPinned && i === 0;
            const handleAttrs = isHandle ? ` draggable="true" data-pin-handle class="pin-cell draggable-handle"` : "";
            return `<td${handleAttrs}><div class="cell-content">${row[h] ?? ""}</div></td>`;
          })
          .join("");

        return `<tr data-row-id="${rowId}" class="${isPinned ? "pinned" : ""}">${cellsHtml}</tr>`;
      })
      .join("");
  }

  static applyRowOffsets(headEl, bodyEl) {
    if (!headEl || !bodyEl) return;
    let currentTop = headEl.offsetHeight;

    bodyEl.querySelectorAll("tr.pinned").forEach((tr) => {
      for (const td of tr.children) {
        Object.assign(td.style, { position: "sticky", top: `${currentTop}px`, zIndex: "2" });
      }
      currentTop += tr.offsetHeight;
    });
  }

  static syncDOMOrder(bodyEl, orderedRows, getRowKey) {
    if (!bodyEl) return;
    const validKeys = new Set(orderedRows.map(getRowKey));
    Array.from(bodyEl.querySelectorAll("tr")).forEach((tr) => {
      if (!validKeys.has(tr.dataset.rowId)) tr.remove();
    });

    const fragment = document.createDocumentFragment();
    for (const row of orderedRows) {
      const tr = bodyEl.querySelector(`tr[data-row-id="${getRowKey(row)}"]`);
      if (tr) fragment.appendChild(tr);
    }
    bodyEl.appendChild(fragment);
  }

  static markTruncatedCells(bodyEl) {
    if (!bodyEl) return;
    requestAnimationFrame(() => {
      const cells = Array.from(bodyEl.querySelectorAll(".cell-content"));
      const isTruncated = cells.map((content) => content.scrollHeight > content.clientHeight);

      cells.forEach((content, i) => {
        content.closest("td")?.classList.toggle("expandable", isTruncated[i]);
      });
    });
  }
}
