package com.x.cloudnext.server.orgsync.source;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;

@Component
public class JdbcO2OrgReadonlySource implements O2OrgReadonlySource {

    private final CloudNextProperties properties;

    public JdbcO2OrgReadonlySource(CloudNextProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<OrgSourceRecord> listUnits() {
        return readonlyJdbcTemplate().query(
                "select xid, xname, xunique, xdistinguishedName, xsuperior, xlevel, xlevelName, xupdateTime, xsequence from ORG_UNIT",
                orgMapper((record, rs) -> {
                    record.setParentId(rs.getString("xsuperior"));
                    record.setLevelValue((Integer) rs.getObject("xlevel"));
                    record.setLevelName(rs.getString("xlevelName"));
                }));
    }

    @Override
    public List<OrgSourceRecord> listPersons() {
        JdbcTemplate jdbcTemplate=readonlyJdbcTemplate();
        String employeeColumn=firstMatchingColumn(requiredColumns(jdbcTemplate,"ORG_PERSON"),"xemployee");
        if (!StringUtils.hasText(employeeColumn)) {
            throw new IllegalStateException("required O2 column ORG_PERSON.xemployee is missing");
        }
        String employeeProjection=", "+employeeColumn+" as employee_no";
        return jdbcTemplate.query(
                "select xid, xname, xunique, xdistinguishedName, xmobile, xmail, xstatus, xupdateTime, xsequence"+employeeProjection+" from ORG_PERSON",
                orgMapper((record, rs) -> {
                    record.setEmployeeNo(rs.getString("employee_no"));
                    record.setMobile(rs.getString("xmobile"));
                    record.setMail(rs.getString("xmail"));
                    record.setStatus(rs.getString("xstatus"));
                }));
    }

    @Override
    public List<OrgSourceRecord> listIdentities() {
        return readonlyJdbcTemplate().query(
                "select xid, xname, xunique, xdistinguishedName, xperson, xunit, xunitName, xunitLevel, xunitLevelName, xmajor, xupdateTime, xsequence from ORG_IDENTITY",
                orgMapper((record, rs) -> {
                    record.setPersonId(rs.getString("xperson"));
                    record.setUnitId(rs.getString("xunit"));
                    record.setUnitName(rs.getString("xunitName"));
                    record.setUnitLevel((Integer) rs.getObject("xunitLevel"));
                    record.setUnitLevelName(rs.getString("xunitLevelName"));
                    Object major = rs.getObject("xmajor");
                    record.setMajorFlag(major == null ? null : Boolean.valueOf(String.valueOf(major)));
                }));
    }

    @Override
    public List<OrgSourceRecord> listRoles() {
        return readonlyJdbcTemplate().query(
                "select xid, xname, xunique, xdistinguishedName, xupdateTime, xsequence from ORG_ROLE",
                orgMapper((record, rs) -> {
                }));
    }

    @Override
    public List<OrgSourceRecord> listGroups() {
        return readonlyJdbcTemplate().query(
                "select xid, xname, xunique, xdistinguishedName, xupdateTime, xsequence from ORG_GROUP",
                orgMapper((record, rs) -> {
                }));
    }

    @Override
    public List<OrgMembershipSourceRecord> listMemberships() {
        JdbcTemplate jdbcTemplate = readonlyJdbcTemplate();
        List<OrgMembershipSourceRecord> memberships = new ArrayList<>();
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_ROLE_personList", "role", "person", "member"));
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_ROLE_groupList", "role", "group", "member"));
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_GROUP_personList", "group", "person", "member"));
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_GROUP_groupList", "group", "group", "member"));
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_GROUP_unitList", "group", "unit", "member"));
        memberships.addAll(loadMemberships(jdbcTemplate, "ORG_GROUP_identityList", "group", "identity", "member"));
        return memberships;
    }

    private List<OrgMembershipSourceRecord> loadMemberships(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String sourceType,
            String targetType,
            String relationType) {
        try {
            List<String> columns = requiredColumns(jdbcTemplate, tableName);
            String targetColumn = detectTargetColumn(columns, targetType);
            String sourceColumn = detectSourceColumn(columns, sourceType, targetColumn);
            if (!StringUtils.hasText(sourceColumn) || !StringUtils.hasText(targetColumn)) {
                throw new IllegalStateException("required O2 membership columns are missing from " + tableName
                        + " (source=" + sourceType + ", target=" + targetType + ")");
            }
            String sequenceColumn = firstMatchingColumn(columns, "xsequence");
            String updateTimeColumn = firstMatchingColumn(columns, "xupdateTime");
            String sql = "select " + sourceColumn + " as source_id, " + targetColumn + " as target_id"
                    + (StringUtils.hasText(sequenceColumn) ? ", " + sequenceColumn + " as sync_sequence" : "")
                    + (StringUtils.hasText(updateTimeColumn) ? ", " + updateTimeColumn + " as sync_update_time" : "")
                    + " from " + tableName + " where " + sourceColumn + " is not null and " + targetColumn
                    + " is not null order by " + sourceColumn + ", " + targetColumn;
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                OrgMembershipSourceRecord record = new OrgMembershipSourceRecord();
                record.setSourceType(sourceType);
                record.setSourceId(rs.getString("source_id"));
                record.setTargetType(targetType);
                record.setTargetId(rs.getString("target_id"));
                record.setRelationType(relationType);
                String syncVersion = sequenceColumn == null ? null : rs.getString("sync_sequence");
                if (!StringUtils.hasText(syncVersion) && updateTimeColumn != null) {
                    syncVersion = rs.getString("sync_update_time");
                }
                record.setSyncVersion(StringUtils.hasText(syncVersion) ? syncVersion
                        : stableRelationshipVersion(sourceType, record.getSourceId(), targetType,
                                record.getTargetId(), relationType));
                return record;
            });
        } catch (DataAccessException e) {
            throw new IllegalStateException("failed to read required O2 membership table " + tableName, e);
        }
    }

    private List<String> requiredColumns(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            List<String> columns = jdbcTemplate.query("select * from " + tableName + " where 1 = 0", rs -> {
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                List<String> result = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    result.add(metaData.getColumnName(i));
                }
                return result;
            });
            if (columns.isEmpty()) {
                throw new IllegalStateException("required O2 table has no readable columns: " + tableName);
            }
            return columns;
        } catch (DataAccessException e) {
            throw new IllegalStateException("required O2 table is missing or unreadable: " + tableName, e);
        }
    }

    private String stableRelationshipVersion(String sourceType, String sourceId, String targetType,
            String targetId, String relationType) {
        String canonical = String.join("\u0000", sourceType, sourceId, targetType, targetId, relationType);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable for O2 membership versioning", e);
        }
    }

    private String detectSourceColumn(List<String> columns, String sourceType, String targetColumn) {
        Map<String, String[]> candidates = new LinkedHashMap<>();
        candidates.put("role", new String[] { "xrole", "xroleid", "role_xid" });
        candidates.put("group", new String[] { "xgroup", "xgroupid", "group_xid" });
        String[] names = candidates.get(sourceType);
        return names == null ? null : firstMatchingColumnExcluding(columns, names, targetColumn);
    }

    private String detectTargetColumn(List<String> columns, String targetType) {
        Map<String, String[]> candidates = new LinkedHashMap<>();
        candidates.put("person", new String[] { "xpersonList", "xperson", "xpersonid" });
        candidates.put("group", new String[] { "xgroupList", "xgroup", "xgroupid" });
        candidates.put("unit", new String[] { "xunitList", "xunit", "xunitid" });
        candidates.put("identity", new String[] { "xidentityList", "xidentity", "xidentityid" });
        String[] names = candidates.get(targetType);
        if (names == null) {
            return null;
        }
        return firstMatchingColumn(columns, names);
    }

    private String firstMatchingColumn(List<String> columns, String... candidates) {
        for (String candidate : candidates) {
            for (String column : columns) {
                if (candidate.equalsIgnoreCase(column)) {
                    return column;
                }
            }
        }
        return null;
    }

    private String firstMatchingColumnExcluding(List<String> columns, String[] candidates, String... excludedColumns) {
        for (String candidate : candidates) {
            for (String column : columns) {
                if (candidate.equalsIgnoreCase(column) && !isExcluded(column, excludedColumns)) {
                    return column;
                }
            }
        }
        return null;
    }

    private boolean isExcluded(String column, String... excludedColumns) {
        for (String excludedColumn : excludedColumns) {
            if (StringUtils.hasText(excludedColumn) && excludedColumn.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private JdbcTemplate readonlyJdbcTemplate() {
        CloudNextProperties.ReadonlyJdbc readonlyJdbc = properties.getReadonlyJdbc();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(readonlyJdbc.getDriverClassName());
        dataSource.setUrl(readonlyJdbc.getUrl());
        dataSource.setUsername(readonlyJdbc.getUsername());
        dataSource.setPassword(readonlyJdbc.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private RowMapper<OrgSourceRecord> orgMapper(OrgRecordEnricher enricher) {
        return (rs, rowNum) -> {
            OrgSourceRecord record = new OrgSourceRecord();
            record.setId(rs.getString("xid"));
            record.setName(rs.getString("xname"));
            record.setUniqueName(rs.getString("xunique"));
            record.setDistinguishedName(rs.getString("xdistinguishedName"));
            record.setUpdatedAt(valueOrNow(rs.getString("xupdateTime")));
            record.setSyncVersion(valueOrNow(rs.getString("xsequence")));
            enricher.enrich(record, rs);
            return record;
        };
    }

    private String valueOrNow(String value) {
        return StringUtils.hasText(value) ? value : Instant.now().toString();
    }

    @FunctionalInterface
    private interface OrgRecordEnricher {
        void enrich(OrgSourceRecord record, java.sql.ResultSet resultSet) throws java.sql.SQLException;
    }
}
