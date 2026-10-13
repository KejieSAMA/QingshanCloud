// These are static source contracts, NOT a substitute for explicitly authorized Java/MySQL tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const tables = JSON.parse(execFileSync(process.execPath, [path.join(root, 'tools/mysql-schema.mjs'), '--json'], { encoding: 'utf8' }));

test('runtime dependency/config cannot fall back to H2 and the converter truncates explicitly', () => {
  assert.doesNotMatch(read('pom.xml'), /com\.h2database|<artifactId>h2<\/artifactId>/);
  const config = read('src/main/resources/application.yml');
  assert.doesNotMatch(config, /jdbc:h2|org\.h2|ddl-auto: (?:update|create)/);
  assert.match(config, /ddl-auto: validate/);
  assert.match(config, /jdbc\.time_zone: UTC/);
  assert.match(config, /driver-class-name: com\.mysql\.cj\.jdbc\.Driver/);
  assert.match(read('src/main/java/com/x/cloudnext/server/config/InstantMicrosecondConverter.java'), /truncatedTo\(ChronoUnit\.MICROS\)/);
  assert.match(read('src/main/java/com/x/cloudnext/server/config/MysqlDataSourceConfig.java'), /setMaximumPoolSize\(5\)/);
});

test('reviewed schema matches all 28 entity mappings and MySQL index limits', () => {
  execFileSync(process.execPath, [path.join(root, 'tools/mysql-schema.mjs')]);
  assert.equal(tables.length, 28);
  for (const table of tables) {
    for (const index of [...table.indexes, ...table.uniques]) {
      assert.ok(index.name.length <= 64, `${table.table}.${index.name}: identifier too long`);
      const bytes = index.columns.reduce((sum, name) => {
        const type = table.fields.find((f) => f.name === name).type;
        const size = /^VARCHAR\((\d+)\)$/.exec(type)?.[1];
        assert.notEqual(type, 'LONGTEXT', 'LOB must not be indexed without explicit review');
        return sum + (size ? Number(size) * 4 : 8);
      }, 0);
      assert.ok(bytes <= 3072, `${table.table}.${index.name}: InnoDB utf8mb4 key too large`);
    }
  }
  const schema = read('src/main/resources/db/mysql/V001__cloudnext.sql').replace(/^--.*$/gm, '');
  assert.doesNotMatch(schema, /\b(?:DROP|USE|CREATE DATABASE|IF NOT EXISTS|CLOB)\b/i);
  assert.equal((schema.match(/ENGINE=InnoDB ROW_FORMAT=DYNAMIC/g) ?? []).length, 28);
  assert.equal((schema.match(/COLLATE utf8mb4_0900_bin/g) ?? []).length, 28);
});

test('all existing application integration entrypoints use the isolated MySQL initializer', () => {
  for (const file of ['drive/AbstractDriveIntegrationTest.java', 'workspace/CloudNextWorkspaceFlowTest.java',
    'link/CloudNextPublicLinkFlowTest.java', 'form/FormLoginSyncIntegrationTest.java']) {
    const code = read(`src/test/java/com/x/cloudnext/server/${file}`);
    assert.doesNotMatch(code, /jdbc:h2|org\.h2|create-drop/);
    assert.match(code, /MysqlTestDatabaseInitializer/);
    assert.match(code, /MysqlDatabaseExtension/);
  }
  assert.match(read('src/test/java/com/x/cloudnext/server/form/FormLoginSyncIntegrationTest.java'), /Replace\.NONE/);
  const helper = read('src/test/java/com/x/cloudnext/server/testing/MysqlTestDatabase.java');
  assert.match(helper, /qingshancloud_test/);
  assert.match(helper, /isolated-mysql/);
  assert.match(helper, /SHOW GRANTS FOR CURRENT_USER/);
  assert.match(read('src/test/resources/junit-platform.properties'), /parallel.enabled=false/);
  assert.match(read('src/main/java/com/x/cloudnext/server/config/CloudNextSchedulingConfig.java'), /ConditionalOnProperty/);
});

test('legacy reader uses MySQL, migration driver is isolated and old input is read-only', () => {
  assert.doesNotMatch(read('src/test/java/com/x/cloudnext/server/orgsync/source/JdbcO2OrgReadonlySourceTest.java'), /jdbc:h2|org\.h2/);
  assert.match(read('tools/h2-to-mysql/pom.xml'), /com\.h2database/);
  const migration = read('tools/h2-to-mysql/src/main/java/com/x/cloudnext/migration/MysqlMigration.java');
  assert.match(migration, /ACCESS_MODE_DATA=r/);
  assert.match(migration, /IFEXISTS=TRUE/);
  assert.match(migration, /CLOUDNEXT_MIGRATION_SOURCE_SHA256/);
  assert.match(migration, /CLOUDNEXT_MIGRATION_SOURCE_TIME_ZONE/);
  assert.match(migration, /GET_LOCK/);
  assert.match(migration, /requireEmptyTarget\(to\)/);
  assert.match(migration, /to\.rollback\(\)/);
  assert.match(migration, /clear-sessions/);
  assert.ok(migration.indexOf('compare(from, to);') < migration.indexOf('to.commit();'));
});
