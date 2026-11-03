// The OA shell supports older system WebViews; these operations are used by the
// existing cloud navigation and dialog code, not just the embedded entry.
if (!Array.prototype.at) Object.defineProperty(Array.prototype, 'at', {
  configurable: true, writable: true,
  value: function (index) {
    const length = Math.min(Math.max(Math.trunc(Number(this.length)) || 0, 0), Number.MAX_SAFE_INTEGER);
    const offset = Math.trunc(Number(index)) || 0;
    const position = offset < 0 ? length + offset : offset;
    return position < 0 || position >= length ? undefined : this[position];
  }
});
if (!Object.hasOwn) Object.defineProperty(Object, 'hasOwn', {
  configurable: true, writable: true,
  value: (object, property) => Object.prototype.hasOwnProperty.call(object, property)
});
