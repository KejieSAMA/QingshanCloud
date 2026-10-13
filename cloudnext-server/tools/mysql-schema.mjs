// Source-only schema inventory/check. Does not compile Java or connect to any database.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const walk = (dir) => fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
  e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
const snake = (s) => s.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);
const quote = (s) => { if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(s)) throw Error('Invalid identifier'); return '`' + s + '`'; };
const tables = walk(path.join(root, 'src/main/java')).filter((f) => f.endsWith('.java')).flatMap((file) => {
  let code = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  if (!/@Entity\b/.test(code)) return [];
  const table = /@Table\(\s*name\s*=\s*"([^"]+)"/.exec(code)?.[1];
  if (!table) throw Error(`Entity without explicit table: ${file}`);
  const parent = /public class \w+ extends (\w+)/.exec(code)?.[1];
  if (parent) {
    const inherited = fs.readFileSync(path.join(path.dirname(file), `${parent}.java`), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
    if (!/@MappedSuperclass\b/.test(inherited)) throw Error(`Unsupported inheritance: ${file}`);
    code += '\n' + inherited;
  }
  const fields = [...code.matchAll(/((?:@[\w.]+(?:\((?:"[^"]*"|[^()"])*\))?\s*)+)(?:private|public|protected)\s+(\w+)\s+(\w+)\s*(?:=[^;]*)?;/g)]
    .filter((m) => /@Column\b/.test(m[1]) && !/@Transient\b/.test(m[1])).map((m) => {
      const a = m[1], col = /@Column\(((?:"[^"]*"|[^()"])*)\)/.exec(a)?.[1] ?? '';
      const name = /name\s*=\s*"([^"]+)"/.exec(col)?.[1] ?? snake(m[3]);
      const length = Number(/length\s*=\s*(\d+)/.exec(col)?.[1] ?? 255);
      const definition = /columnDefinition\s*=\s*"([^"]+)"/.exec(col)?.[1];
      let type = /@Lob\b/.test(a) ? 'LONGTEXT' : m[2] === 'String' || /@Enumerated/.test(a) ? `VARCHAR(${length})`
        : /^(long|Long)$/.test(m[2]) ? 'BIGINT' : /^(int|Integer)$/.test(m[2]) ? 'INT'
          : /^(boolean|Boolean)$/.test(m[2]) ? 'BIT(1)' : m[2] === 'Instant' ? 'DATETIME(6)' : null;
      if (!type) throw Error(`Unknown mapping ${table}.${name}: ${m[2]}`);
      // Keep declared defaults, but booleans map to Hibernate's BIT representation.
      const defaultValue = (definition && /\bDEFAULT\s+(.+)$/i.exec(definition)?.[1])
        || /@(?:org\.hibernate\.annotations\.)?ColumnDefault\("([^"]+)"\)/.exec(a)?.[1];
      return { name, type, defaultValue, nullable: !/nullable\s*=\s*false/.test(col) && !/@Id\b/.test(a),
        primary: /@Id\b/.test(a), unique: /unique\s*=\s*true/.test(col) };
    });
  if (!fields.length || fields.filter((f) => f.primary).length !== 1) throw Error(`Invalid entity fields: ${table}`);
  const indexes = [...code.matchAll(/@Index\(([^)]*)\)/g)].map((m) => ({
    name: /name\s*=\s*"([^"]+)"/.exec(m[1])?.[1],
    columns: /columnList\s*=\s*"([^"]+)"/.exec(m[1])?.[1].split(',').map((s) => s.trim())
  }));
  const uniques = [...code.matchAll(/@UniqueConstraint\(([^)]*)\)/g)].map((m, i) => ({
    name: /name\s*=\s*"([^"]+)"/.exec(m[1])?.[1] ?? `uk_${table.replace('cloudnext_', '')}_${i + 1}`,
    columns: [...m[1].slice(m[1].indexOf('columnNames')).matchAll(/"([^"]+)"/g)].map((x) => x[1])
  }));
  for (const f of fields.filter((x) => x.unique)) uniques.push({ name: `uk_${table.replace('cloudnext_', '')}_${f.name}`, columns: [f.name] });
  for (const index of [...indexes, ...uniques]) {
    if (!index.name || !index.columns?.length || index.columns.some((c) => !fields.some((f) => f.name === c)))
      throw Error(`Invalid index: ${table}.${index.name}`);
  }
  return [{ table, fields, indexes, uniques }];
}).sort((a, b) => a.table.localeCompare(b.table, 'en'));
if (tables.length !== 28) throw Error(`Expected 28 business tables, found ${tables.length}; review the baseline`);
const sql = '-- V001: CloudNext MySQL 8.0 baseline. Apply explicitly to an EMPTY dedicated database.\n'
  + '-- No CREATE DATABASE, USE, DROP, IF NOT EXISTS or cross-schema statements.\n\n'
  + tables.map(({ table, fields, indexes, uniques }) => {
    const parts = fields.map((f) => `  ${quote(f.name)} ${f.type}${f.nullable ? '' : ' NOT NULL'}${f.defaultValue ? ` DEFAULT ${f.defaultValue}` : ''}`);
    parts.push(`  PRIMARY KEY (${quote(fields.find((f) => f.primary).name)})`);
    for (const u of uniques) parts.push(`  UNIQUE KEY ${quote(u.name)} (${u.columns.map(quote).join(', ')})`);
    for (const i of indexes) parts.push(`  KEY ${quote(i.name)} (${i.columns.map(quote).join(', ')})`);
    return `CREATE TABLE ${quote(table)} (\n${parts.join(',\n')}\n) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;`;
  }).join('\n\n') + '\n';
const baseline = path.join(root, 'src/main/resources/db/mysql/V001__cloudnext.sql');
if (process.argv.includes('--print')) process.stdout.write(sql);
else if (process.argv.includes('--json')) process.stdout.write(JSON.stringify(tables));
else {
  if (fs.readFileSync(baseline, 'utf8').replace(/\r\n/g, '\n') !== sql) throw Error('MySQL baseline differs from entity inventory; review and update explicitly');
  console.log(`MySQL baseline matches ${tables.length} entities (columns, indexes, unique constraints).`);
}
