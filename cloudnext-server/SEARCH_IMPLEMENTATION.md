# Unified name search

The new `GET /api/search` uses the existing CloudNext token and returns only accessible names. `/api/search/files` remains unchanged for existing clients. This change does not create database tables or extract document contents.

## Request

- `keyword`: required, trimmed, 1–100 characters; case-insensitive AND match for whitespace-separated terms against names only.
- `scope`: `all` (default), `personal`, `shared`, `workspace`.
- `itemType`: `all`, `file`, `folder`, `workspace`.
- `fileType`: empty, `word`, `excel`, `ppt`, `pdf`, `image`, `video`, `audio`, `archive`, `other`. Other includes remaining stored classifications.
- `owner`: canonical person key; optional choices returned in `owners` are limited to visible matches before applying this one filter.
- `updatedFrom` / `updatedTo`: UTC instants, inclusive start / exclusive end. UI date selection converts the end day to the following local midnight.
- `sortBy`: `relevance` (default), `name`, `updatedAt`; `sortOrder`: `asc` / `desc`. Relevance always uses exact, prefix, contains, then newest first and stable ID. Names use Chinese collation with numeric segments.
- `page`: one-based, clamped to available pages; `size`: defaults to 30, maximum 100.

## Response

`page`, `size`, `total`, `totalPages`, `hasNext`, `owners`, `items`.

Each item extends the existing file metadata response with `resultType`, `source`, `workspaceId`, `shareId`, `locationParentId`, `displayPath`, `allowPreview`, `allowDownload`, `allowLocate`. Workspace results use the space ID, creator and modification timestamps. Shared paths start at the authorized root; shared root items never return their private parent ID.

Personal results take precedence over workspace membership results, which take precedence over shared results in an all-scope search. Multiple shares for a file are deduplicated and a downloadable effective entry is preferred. Invalid expiry values fail closed. Missing/deleted ancestors, cross-scope links and cycles are excluded before counting and paging. Workspace VIEWER results cannot be downloaded. Opening and downloading continue through the existing permission-checked APIs.

## Validation and rollout

Frontend targeted tests: `searchResults.test.js`, `useSearchResults.test.js`. UI fixture checks cover desktop and 375px mobile layouts; fixtures are not production data and the temporary fixture page was removed.

After explicit permission to compile, run Maven tests `UnifiedSearchServiceTest`, `CloudNextUnifiedSearchFlowTest`, and existing `CloudNextSearchFlowTest` in the test database. They cover shared descendants, PERSON/UNIT/IDENTITY canonical aliases, revocation, role restrictions, path boundaries, natural ordering, validation and legacy endpoint compatibility.

Build and restart the backend using the existing runtime configuration; retain the previous JAR for rollback. The frontend dev server serves the new source immediately, but the existing deployed JAR does not contain `/api/search`. Until the new backend is running, the results page reports that search is not deployed; this is not an empty search result.

Verify with an authorized real account across personal, shared and workspace sources. Check preview/download/locate, revoke a test share and recheck visibility, verify URL/back/mobile paging, and measure repeated searches using the `op=unified-search` timing log (keyword content is never logged). Target P95 below one second on the current data size. Real-account verification and P95 have not been performed before backend build authorization.
