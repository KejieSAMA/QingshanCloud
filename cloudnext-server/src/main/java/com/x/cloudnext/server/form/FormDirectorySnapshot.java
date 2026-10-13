package com.x.cloudnext.server.form;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class FormDirectorySnapshot {
    private String tenantId;
    private String foundationSot;
    private String foundationWriteImpl;
    private String generatedAt;
    private Integer schemaVersion;
    private Authority authority;
    @JsonAlias({"people","persons"}) private List<Employee> employees=new ArrayList<>();
    @JsonAlias({"orgs","units"}) private List<Organization> organizations=new ArrayList<>();
    @JsonAlias({"identities","employments"}) private List<Assignment> assignments=new ArrayList<>();
    private List<Position> positions=new ArrayList<>();
    private List<Role> roles=new ArrayList<>();
    private List<RoleBinding> roleBindings=new ArrayList<>();

    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getFoundationSot(){return foundationSot;} public void setFoundationSot(String v){foundationSot=v;}
    public String getFoundationWriteImpl(){return foundationWriteImpl;} public void setFoundationWriteImpl(String v){foundationWriteImpl=v;}
    public String getGeneratedAt(){return generatedAt;} public void setGeneratedAt(String v){generatedAt=v;}
    public Integer getSchemaVersion(){return schemaVersion;} public void setSchemaVersion(Integer v){schemaVersion=v;}
    public Authority getAuthority(){return authority;} public void setAuthority(Authority v){authority=v;}
    public String authorityRead(){return authority==null?foundationSot:authority.getRead();}
    public String authorityWrite(){return authority==null?foundationWriteImpl:authority.getWrite();}
    public List<Employee> getEmployees(){return employees;} public void setEmployees(List<Employee> v){employees=v==null?new ArrayList<>():v;}
    public List<Organization> getOrganizations(){return organizations;} public void setOrganizations(List<Organization> v){organizations=v==null?new ArrayList<>():v;}
    public List<Assignment> getAssignments(){return assignments;} public void setAssignments(List<Assignment> v){assignments=v==null?new ArrayList<>():v;}
    public List<Position> getPositions(){return positions;} public void setPositions(List<Position> v){positions=v==null?new ArrayList<>():v;}
    public List<Role> getRoles(){return roles;} public void setRoles(List<Role> v){roles=v==null?new ArrayList<>():v;}
    public List<RoleBinding> getRoleBindings(){return roleBindings;} public void setRoleBindings(List<RoleBinding> v){roleBindings=v==null?new ArrayList<>():v;}

    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class Employee {
        @JsonAlias({"id","formEmployeeId"}) private String employeeId;
        @JsonAlias({"employeeNumber","workNo"}) private String employeeNo;
        @JsonAlias({"displayName"}) private String name;
        private String displayPath;
        @JsonAlias({"enabled"}) private boolean accountEnabled;
        @JsonAlias({"tokenVersion"}) private long authVersion;
        private String status;
        private boolean deleted;
        private boolean tombstone;
        private Account account;
        private boolean mustChangePassword;
        private List<String> roleCodes=new ArrayList<>();
        public String getEmployeeId(){return employeeId;} public void setEmployeeId(String v){employeeId=v;}
        public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}
        public String getName(){return name;} public void setName(String v){name=v;}
        public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
        public boolean isAccountEnabled(){return account==null?accountEnabled:account.isEnabled()&&!account.isTombstone();} public void setAccountEnabled(boolean v){accountEnabled=v;}
        public long getAuthVersion(){return account==null?authVersion:account.getAuthVersion();} public void setAuthVersion(long v){authVersion=v;}
        public String getStatus(){return status;} public void setStatus(String v){status=v;}
        public boolean isDeleted(){return deleted||tombstone;} public void setDeleted(boolean v){deleted=v;}
        public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;}
        public Account getAccount(){return account;} public void setAccount(Account v){account=v;}
        public boolean isMustChangePassword(){return account==null?mustChangePassword:account.isMustChangePassword();} public void setMustChangePassword(boolean v){mustChangePassword=v;}
        public List<String> getRoleCodes(){return roleCodes;} public void setRoleCodes(List<String> v){roleCodes=v==null?new ArrayList<>():v;}
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class Organization {
        @JsonAlias({"id","formOrgId"}) private String orgId;
        @JsonAlias({"parentId"}) private String parentOrgId;
        private String name; private String displayPath; private String status; private boolean deleted; private boolean tombstone;
        public String getOrgId(){return orgId;} public void setOrgId(String v){orgId=v;}
        public String getParentOrgId(){return parentOrgId;} public void setParentOrgId(String v){parentOrgId=v;}
        public String getName(){return name;} public void setName(String v){name=v;}
        public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
        public String getStatus(){return status;} public void setStatus(String v){status=v;}
        public boolean isDeleted(){return deleted||tombstone;} public void setDeleted(boolean v){deleted=v;}
        public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;}
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class Assignment {
        @JsonAlias({"id","formAssignmentId"}) private String assignmentId;
        private String employeeId; @JsonAlias({"unitId"}) private String orgId; private String positionId;
        @JsonAlias({"positionTitle"}) private String name; private String displayPath; @JsonAlias({"major","primary","isPrimary"}) private boolean primaryAssignment;
        @JsonAlias({"enabled"}) private boolean active=true; private String status; private boolean deleted; private boolean tombstone;
        public String getAssignmentId(){return assignmentId;} public void setAssignmentId(String v){assignmentId=v;}
        public String getEmployeeId(){return employeeId;} public void setEmployeeId(String v){employeeId=v;}
        public String getOrgId(){return orgId;} public void setOrgId(String v){orgId=v;}
        public String getPositionId(){return positionId;} public void setPositionId(String v){positionId=v;}
        public String getName(){return name;} public void setName(String v){name=v;}
        public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
        public boolean isPrimaryAssignment(){return primaryAssignment;} public void setPrimaryAssignment(boolean v){primaryAssignment=v;}
        public boolean isActive(){return active&&!tombstone;} public void setActive(boolean v){active=v;}
        public String getStatus(){return status;} public void setStatus(String v){status=v;}
        public boolean isDeleted(){return deleted||tombstone;} public void setDeleted(boolean v){deleted=v;}
        public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;}
    }
    @JsonIgnoreProperties(ignoreUnknown=true) public static class Account { private boolean enabled; private long authVersion; private boolean mustChangePassword; private boolean tombstone; public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public long getAuthVersion(){return authVersion;} public void setAuthVersion(long v){authVersion=v;} public boolean isMustChangePassword(){return mustChangePassword;} public void setMustChangePassword(boolean v){mustChangePassword=v;} public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;} }
    @JsonIgnoreProperties(ignoreUnknown=true) public static class Position { @JsonAlias("positionId") private String id; private String name; public String getId(){return id;} public void setId(String v){id=v;} public String getName(){return name;} public void setName(String v){name=v;} }
    @JsonIgnoreProperties(ignoreUnknown=true) public static class Role { @JsonAlias("roleId") private String id; private String code; private String name; private boolean tombstone; public String getId(){return id;} public void setId(String v){id=v;} public String getCode(){return code;} public void setCode(String v){code=v;} public String getName(){return name;} public void setName(String v){name=v;} public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;} }
    @JsonIgnoreProperties(ignoreUnknown=true) public static class RoleBinding { private String roleId; private String employeeId; private boolean tombstone; public String getRoleId(){return roleId;} public void setRoleId(String v){roleId=v;} public String getEmployeeId(){return employeeId;} public void setEmployeeId(String v){employeeId=v;} public boolean isTombstone(){return tombstone;} public void setTombstone(boolean v){tombstone=v;} }
    @JsonIgnoreProperties(ignoreUnknown=true) public static class Authority { private String read;private String write;public String getRead(){return read;}public void setRead(String v){read=v;}public String getWrite(){return write;}public void setWrite(String v){write=v;} }
}
