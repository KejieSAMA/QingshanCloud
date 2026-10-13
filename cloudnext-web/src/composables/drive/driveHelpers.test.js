import { describe, expect, it } from 'vitest';
import { appendBreadcrumb, canReuseBreadcrumbs, cloneBreadcrumbs, sliceBreadcrumbsTo } from './navigation';
import { createRequestTracker } from './requestTracker';

describe('drive breadcrumb helpers', () => {
  it('clones and reuses breadcrumb chains for the current folder', () => {
    const original = [
      { id: 'root-a', name: 'Root A' },
      { id: 'child-b', name: 'Child B' }
    ];

    const cloned = cloneBreadcrumbs(original);
    expect(cloned).toEqual(original);
    expect(cloned).not.toBe(original);
    expect(canReuseBreadcrumbs(cloned, 'child-b')).toBe(true);
    expect(canReuseBreadcrumbs(cloned, 'root-a')).toBe(false);

    const appended = appendBreadcrumb(cloned, { id: 'leaf-c', name: 'Leaf C' });
    expect(appended).toEqual([
      { id: 'root-a', name: 'Root A' },
      { id: 'child-b', name: 'Child B' },
      { id: 'leaf-c', name: 'Leaf C' }
    ]);

    expect(sliceBreadcrumbsTo(appended, 'child-b')).toEqual([
      { id: 'root-a', name: 'Root A' },
      { id: 'child-b', name: 'Child B' }
    ]);
    expect(sliceBreadcrumbsTo(appended, '')).toEqual([]);
  });
});

describe('drive request tracker', () => {
  it('aborts stale requests and only keeps the latest request id current', () => {
    const tracker = createRequestTracker();

    const first = tracker.begin();
    expect(tracker.isCurrent(first.requestId)).toBe(true);
    expect(first.signal.aborted).toBe(false);

    const second = tracker.begin();
    expect(first.signal.aborted).toBe(true);
    expect(tracker.isCurrent(first.requestId)).toBe(false);
    expect(tracker.isCurrent(second.requestId)).toBe(true);

    tracker.cancel();
    expect(second.signal.aborted).toBe(true);
    expect(tracker.isCurrent(second.requestId)).toBe(false);
  });
});
