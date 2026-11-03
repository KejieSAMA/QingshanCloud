// Source contracts only: no Java compilation, server startup or database connection.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const java = 'src/main/java/com/x/cloudnext/server/';
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const entities = ['drive/entity/DriveItem', 'drive/entity/DriveShare', 'drive/entity/DrivePublicLink',
  'workspace/entity/Workspace', 'workspace/entity/WorkspaceMember', 'workspace/entity/UserPreference',
  'audit/entity/AuditLog', 'link/entity/DrivePublicLinkAccessLog', 'drive/next/DriveUserItem',
  'drive/next/DriveItemVersion', 'drive/next/DriveWriteReceipt', 'drive/next/StorageCleanup'];

test('all file-domain entities participate in query and ID-read isolation', () => {
  for (const entity of entities) {
    const source = read(java + entity + '.java');
    assert.match(source, /extends com\.x\.cloudnext\.server\.namespace\.DriveScopedEntity/);
    assert.match(source, /@org\.hibernate\.annotations\.Filter\(name = "driveNamespace", condition = "drive_id = :driveId"\)/);
    assert.match(source, /idx_namespace_/);
  }
  const base = read(java + 'namespace/DriveScopedEntity.java');
  assert.match(base, /updatable = false/);
  assert.match(base, /@PostLoad @PreUpdate @PreRemove/);
  assert.match(base, /DriveContext\.assertSame\(driveId\)/);
  assert.match(base, /DriveContext\.requireId\(\)/);
});
test('every transaction applies the verified scope and requests clear thread state', () => {
  const config = read(java + 'namespace/DriveIsolationConfiguration.java');
  assert.match(config, /super\.doBegin[\s\S]*DriveContext\.apply/);
  assert.match(config, /finally \{ DriveContext\.clear\(\); \}/);
  const access = read(java + 'drive/DriveScopeAccessService.java');
  assert.match(access, /session\.getDrive\(\)\.getId\(\)\.equals\(item\.getDriveId\(\)\)/);
  const walk = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? walk(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
  const models = new Set(entities.map(entity => entity.split('/').at(-1)));
  const repositories = walk(path.join(root, java)).filter(file => file.endsWith('Repository.java')).map(file => fs.readFileSync(file, 'utf8'))
    .filter(source => models.has(/extends JpaRepository<\s*(\w+)/.exec(source)?.[1]));
  assert.equal(repositories.length, entities.length);
  for (const source of repositories) assert.match(source, /@org\.springframework\.transaction\.annotation\.Transactional\(readOnly = true\)/);
});
test('bulk updates cannot bypass the namespace', () => {
  for (const entity of ['DriveItemRepository', 'DrivePublicLinkRepository']) {
    assert.match(read(java + 'drive/repository/' + entity + '.java'), /driveId = :#\{T\(com\.x\.cloudnext\.server\.namespace\.DriveContext\)\.requireId\(\)\}/);
  }
});
test('exclusive links and editor tickets renew admission and bind their resources', () => {
  const namespaces = read(java + 'namespace/DriveNamespaceService.java');
  assert.match(namespaces, /enterPublicLink[\s\S]*sessions\.currentSession\(token\)/);
  assert.match(namespaces, /enterTicket[\s\S]*form\.resolveDrive\(tenant, employee/);
  const tickets = read(java + 'drive/DrivePreviewTicketService.java');
  assert.match(tickets, /namespaces\.enterTicket\(payload\.getDriveId/);
  assert.match(tickets, /link\.isRevoked\(\)/);
  assert.match(tickets, /access\.requireItem/);
  assert.match(namespaces, /enterLegacyTicket[\s\S]*!"default"\.equals/);
});
test('migration is explicit, stops on ambiguity, and preserves file references', () => {
  const migration = read('src/main/resources/db/mysql/V002__drive_namespaces.sql');
  assert.match(migration, /SIGNAL SQLSTATE '45000'/);
  assert.match(migration, /Mixed or mismatching Form tenants/);
  assert.match(migration, /already started/);
  assert.match(migration, /SHA2\(CONCAT\('cloudnext:'/);
  assert.doesNotMatch(migration, /(?:UPDATE|SET)\s+(?:storage_key|canonical_person_id|person_oa_id)\b/i);
  assert.doesNotMatch(read('src/main/resources/application.yml'), /V002__drive_namespaces|flyway|liquibase/);
  for (const model of entities) {
    const table = /@Table\(\s*name\s*=\s*"([^"]+)"/.exec(read(java + model + '.java'))[1];
    assert.ok(migration.includes('ALTER TABLE ' + table + ' ADD COLUMN drive_id'), table);
    assert.ok(migration.includes('UPDATE ' + table + ' SET drive_id = namespace_value'), table);
  }
});
test('archiving a drive pauses future expiry cleanup rather than deleting its files', () => {
  const namespaces = read(java + 'namespace/DriveNamespaceService.java');
  assert.match(namespaces, /if \(maintenanceAllowed\(\)\) work\.run\(\)/);
  assert.match(namespaces, /form\.driveEnabled\(/);
  assert.match(read(java + 'drive/DriveService.java'), /if \(!namespaces\.maintenanceAllowed\(\)\) break/);
});
test('SSO attempts have independent cookies and one-time completion locks', () => {
  assert.match(read(java + 'web/CloudNextAuthController.java'), /SSO_COOKIE \+ "_" \+ result\.getState\(\)/);
  assert.match(read(java + 'auth/SsoBootstrapService.java'), /namespaceId\("sso", cookieValue\)/);
  assert.match(read(java + 'auth/repository/SsoBootstrapRepository.java'), /PESSIMISTIC_WRITE/);
  assert.match(read(java + 'auth/SsoBootstrapService.java'), /bootstraps\.findForUpdate/);
  assert.match(read(java + 'web/CloudNextAuthController.java'), /legacy SSO completion only supports the default drive/);
});
