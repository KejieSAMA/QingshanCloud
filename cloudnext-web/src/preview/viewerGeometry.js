export const rotation = n => ((Math.round(n / 90) * 90 % 360) + 360) % 360;
export const clampZoom = n => Math.max(.5, Math.min(4, Math.round(n * 100) / 100));
export function rotatedSize(width, height, angle) { return rotation(angle) % 180 ? { width: height, height: width } : { width, height }; }
export function fitScale(width, height, viewportWidth, viewportHeight) {
  return Math.min(1, Math.max(.05, (viewportWidth - 32) / Math.max(1, width)), Math.max(.05, (viewportHeight - 32) / Math.max(1, height)));
}
export const pageNumber = (value, total) => /^\d+$/.test(String(value)) && +value >= 1 && +value <= total ? +value : null;
export function zoomAnchor(scroll, anchor, before, after) { return Math.max(0, (scroll + anchor) / Math.max(1, before) * after - anchor); }
