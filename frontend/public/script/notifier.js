export const NotificationSource = Object.freeze({
  ROW_COPY: "ROW_COPY",
  NO_RESULTS: "NO_RESULTS",
  SEARCH_RESULTS: "SEARCH_RESULTS",
  SERVER_ERROR: "SERVER_ERROR",
});

export class StatusNotifier {
  static MESSAGES = {
    [NotificationSource.ROW_COPY]: "Copied row to clipboard",
    [NotificationSource.NO_RESULTS]: "No results found",
    [NotificationSource.SEARCH_RESULTS]: (count) => `Found ${count.toLocaleString()} result${count === 1 ? "" : "s"}`,
    [NotificationSource.SERVER_ERROR]: "Server is down or the VM expired.",
  };

  constructor(element, displayDurationMs = 2500) {
    this.element = element;
    this.displayDurationMs = displayDurationMs;
    this.timeoutId = null;
  }

  notify(sourceIdentifier, payload = null) {
    if (!this.element) return;

    let message = StatusNotifier.MESSAGES[sourceIdentifier];
    if (typeof message === "function") {
      message = message(payload);
    }
    if (!message) return;

    if (this.timeoutId) clearTimeout(this.timeoutId);

    this.element.textContent = message;
    this.element.classList.add("is-visible");

    this.timeoutId = setTimeout(() => {
      this.element.classList.remove("is-visible");
      this.timeoutId = null;
    }, this.displayDurationMs);
  }
}
