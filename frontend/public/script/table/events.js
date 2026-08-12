import { Render } from "./render.js";

// Calls preventDefault only if the event supports it, avoiding console warnings on passive listeners.
const preventIfCancelable = (e) => e.cancelable && e.preventDefault();

// Handles all DOM event wiring for the results table (drag/drop, touch, hover-copy).
export class TableEvents {
  constructor(tableInstance) {
    this.table = tableInstance;
    this.draggedHeader = null;
    this.draggedRowId = null;
  }

  init() {
    const { head, body, wrapper } = this.table.elements;

    if (head) {
      head.addEventListener("dragstart", (e) => this.handleHeaderDragStart(e));
      head.addEventListener("dragover", (e) => e.preventDefault());
      head.addEventListener("drop", (e) => this.handleHeaderDrop(e));
      if (this.table.isMobile) this.initMobileHeaderTouch();
    }

    if (wrapper && !this.table.isMobile) {
      wrapper.addEventListener(
        "scroll",
        () => {
          if (this.table.roamingBtn) this.table.roamingBtn.classList.remove("is-active");
          Render.applyRowOffsets(head, body);
        },
        { passive: true },
      );
    }

    if (body) {
      body.addEventListener("click", (e) => {
        const td = e.target.closest("td.expandable");
        td?.querySelector(".cell-content")?.classList.toggle("expanded");
      });

      body.addEventListener("dblclick", (e) => {
        const tr = e.target.closest("tr");
        if (tr) this.table.togglePinRow(tr);
      });

      body.addEventListener("dragstart", (e) => this.handleRowDragStart(e));
      body.addEventListener("dragover", (e) => {
        if (this.draggedRowId !== null) e.preventDefault();
      });
      body.addEventListener("drop", (e) => this.handleRowDrop(e));

      if (this.table.isMobile) this.initMobileRowTouch();
      else this.initDesktopHoverAndCopy();
    }
  }

  handleHeaderDragStart(e) {
    const th = e.target.closest("th");
    if (!th) return;
    this.draggedHeader = th.dataset.header;
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", this.draggedHeader);
  }

  handleHeaderDrop(e) {
    e.preventDefault();
    const th = e.target.closest("th");
    if (!th || !this.draggedHeader) return;

    this.table.reorderHeader(this.draggedHeader, th.dataset.header);
    this.draggedHeader = null;
  }

  handleRowDragStart(e) {
    const handle = e.target.closest("[data-pin-handle]");
    const tr = handle?.closest("tr.pinned");
    if (!tr) return e.preventDefault();

    this.draggedRowId = tr.dataset.rowId;
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", this.draggedRowId);
  }

  handleRowDrop(e) {
    const tr = e.target.closest("tr.pinned");
    if (!tr || this.draggedRowId === null) return;
    e.preventDefault();

    this.table.reorderPinnedRow(this.draggedRowId, tr.dataset.rowId);
    this.draggedRowId = null;
  }

  initDesktopHoverAndCopy() {
    const { head, body, wrapper } = this.table.elements;
    const hideRoamingBtn = () => this.table.roamingBtn?.classList.remove("is-active");

    const showRoamingBtn = (tr) => {
      if (!this.table.roamingBtn || !wrapper || !tr.dataset.rowId) return hideRoamingBtn();

      const frameRect = wrapper.parentElement.getBoundingClientRect();
      const trRect = tr.getBoundingClientRect();
      const wrapperRect = wrapper.getBoundingClientRect();
      const headHeight = head?.offsetHeight || 0;
      const isPinned = tr.classList.contains("pinned");
      const topLimit = wrapperRect.top + headHeight + (isPinned ? 0 : this.table.getPinnedRowsHeight());

      if (trRect.top < topLimit - 1 || trRect.bottom > wrapperRect.bottom + 1) return hideRoamingBtn();

      this.table.roamingBtn.style.top = `${trRect.top - frameRect.top}px`;
      this.table.roamingBtn.style.height = `${trRect.height}px`;

      const btn = this.table.roamingBtn.querySelector(".btn-pin-copy");
      if (btn) btn.dataset.rowId = tr.dataset.rowId;

      this.table.roamingBtn.classList.add("is-active");
    };

    body.addEventListener("mousemove", (e) => {
      const tr = e.target.closest("tr");
      if (!tr || !tr.dataset.rowId) return hideRoamingBtn();
      showRoamingBtn(tr);
    });

    wrapper.parentElement?.addEventListener("mouseleave", hideRoamingBtn);

    if (this.table.copyContainer) {
      this.table.copyContainer.addEventListener("click", (e) => {
        const btn = e.target.closest(".btn-pin-copy");
        if (!btn) return;
        const targetRow = this.table.getRowForCopy(btn.dataset.rowId);
        if (targetRow) this.table.onRowCopy?.(targetRow);
      });
    }
  }

  initMobileHeaderTouch() {
    const { head } = this.table.elements;
    let activeHeader = null,
      startX = 0,
      isDragging = false;

    head.addEventListener(
      "touchstart",
      (e) => {
        const th = e.target.closest("th");
        if (!th) return;
        activeHeader = th.dataset.header;
        isDragging = false;
        startX = e.touches[0].clientX;
      },
      { passive: true },
    );

    head.addEventListener(
      "touchmove",
      (e) => {
        if (!activeHeader) return;
        if (Math.abs(e.touches[0].clientX - startX) > 5) {
          isDragging = true;
          preventIfCancelable(e);
        }
      },
      { passive: false },
    );

    head.addEventListener("touchend", (e) => {
      if (activeHeader && isDragging) {
        const touch = e.changedTouches[0];
        const targetTh = document.elementFromPoint(touch.clientX, touch.clientY)?.closest("th");
        if (targetTh && targetTh.dataset.header !== activeHeader) {
          this.table.reorderHeader(activeHeader, targetTh.dataset.header);
        }
      }
      activeHeader = null;
      isDragging = false;
    });

    head.addEventListener("touchcancel", () => {
      activeHeader = null;
      isDragging = false;
    });
  }

  initMobileRowTouch() {
    let touchTimer = null,
      startX = 0,
      startY = 0,
      longPressTriggered = false;
    let lastTapTime = 0,
      lastTapRowId = null,
      activeDragRowId = null,
      isDraggingRow = false;

    const clearTimer = () => {
      if (touchTimer) {
        clearTimeout(touchTimer);
        touchTimer = null;
      }
    };
    const touchXY = (e) => [e.touches[0].clientX, e.touches[0].clientY];

    this.table.elements.body.addEventListener(
      "touchstart",
      (e) => {
        const tr = e.target.closest("tr");
        if (!tr) return;

        if (e.target.closest("[data-pin-handle]") && tr.classList.contains("pinned")) {
          activeDragRowId = tr.dataset.rowId;
          isDraggingRow = false;
          [startX, startY] = touchXY(e);
          return;
        }

        longPressTriggered = false;
        [startX, startY] = touchXY(e);

        touchTimer = setTimeout(() => {
          longPressTriggered = true;
          const targetRow = this.table.getRowForCopy(tr.dataset.rowId);
          if (targetRow) {
            this.table.onRowCopy?.(targetRow);
            if (navigator.vibrate) navigator.vibrate(50);
          }
        }, 500);
      },
      { passive: true },
    );

    this.table.elements.body.addEventListener(
      "touchmove",
      (e) => {
        if (activeDragRowId !== null) {
          if (Math.abs(e.touches[0].clientY - startY) > 5) {
            isDraggingRow = true;
            preventIfCancelable(e);
          }
          return;
        }
        if (touchTimer && Math.hypot(e.touches[0].clientX - startX, e.touches[0].clientY - startY) > 10) clearTimer();
      },
      { passive: false },
    );

    this.table.elements.body.addEventListener("touchend", (e) => {
      if (activeDragRowId !== null) {
        if (isDraggingRow) {
          const touch = e.changedTouches[0];
          const targetTr = document.elementFromPoint(touch.clientX, touch.clientY)?.closest("tr.pinned");
          if (targetTr && targetTr.dataset.rowId !== activeDragRowId) {
            this.table.reorderPinnedRow(activeDragRowId, targetTr.dataset.rowId);
          }
        }
        activeDragRowId = null;
        isDraggingRow = false;
        return;
      }

      const tr = e.target.closest("tr");
      clearTimer();

      if (longPressTriggered) {
        preventIfCancelable(e);
        lastTapTime = 0;
        return;
      }

      if (!tr) return;
      const now = Date.now();
      const currentRowId = tr.dataset.rowId;

      if (now - lastTapTime < 300 && lastTapRowId === currentRowId) {
        preventIfCancelable(e);
        this.table.togglePinRow(tr);
        lastTapTime = 0;
        lastTapRowId = null;
      } else {
        lastTapTime = now;
        lastTapRowId = currentRowId;
      }
    });

    this.table.elements.body.addEventListener("touchcancel", () => {
      clearTimer();
      activeDragRowId = null;
      isDraggingRow = false;
    });
  }
}
