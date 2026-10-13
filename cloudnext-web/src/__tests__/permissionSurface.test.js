import { readFileSync, existsSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
const read = path => readFileSync(new URL(path, import.meta.url),'utf8');
describe('CloudNext permission surface after desktop cutover', () => {
 it('deletes old desktop action tables and retains mobile permission checks',()=>{
  expect(existsSync(new URL('../components/drive/DriveFileTable.vue',import.meta.url))).toBe(false);
  const source=read('../mobile/pages/WorkspaceDetailPage.vue');
  expect(source).toContain('if (!canWrite.value)');
  expect(source).toContain('canManageMembers');
  expect(source).toContain('roleCanDeleteWorkspace');
 });
 it('desktop actions depend on server presentation permissions',()=>{
  const source=read('../next/operations.js');
  expect(source).toContain('allowCopy');
  expect(source).toContain('allowDownload');
  expect(source).toContain('allowManage');
 });
 it('mobile share creation does not submit canReshare',()=>{
  const source=read('../mobile/pages/MyFilesPage.vue');
  expect(source).toContain('await createDriveShares({');
  expect(source).not.toContain('shareCanReshare');
 });
});
