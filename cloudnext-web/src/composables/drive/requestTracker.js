export function createRequestTracker() {
  let currentRequestId = 0;
  let controller = null;

  return {
    begin() {
      currentRequestId += 1;
      controller?.abort();
      controller = new AbortController();
      return {
        requestId: currentRequestId,
        signal: controller.signal
      };
    },
    isCurrent(requestId) {
      return requestId === currentRequestId;
    },
    cancel() {
      currentRequestId += 1;
      controller?.abort();
      controller = null;
    }
  };
}
