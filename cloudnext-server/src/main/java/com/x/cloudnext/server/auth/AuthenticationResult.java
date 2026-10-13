package com.x.cloudnext.server.auth;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationResult {
    private String tenantId;
    private String formEmployeeId;
    private String employeeNo;
    private String personKey;
    private String name;
    private String displayPath;
    private String authProvider;
    private long authVersion;
    private boolean accountEnabled;
    private boolean mustChangePassword;
    private String returnTo;
    private String audience;
    private String mappingDecision;
    private String tokenType="user";
    private List<String> roleList=new ArrayList<>();
    private List<AuthIdentityDto> identityList=new ArrayList<>();

    public static AuthenticationResult fromO2(O2AuthenticationResult source) {
        AuthenticationResult r=new AuthenticationResult();
        r.personKey=source.getPersonOaId(); r.name=source.getName();
        r.displayPath=source.getDistinguishedName(); r.authProvider="O2OA";
        r.accountEnabled=true; r.mustChangePassword=Boolean.TRUE.equals(source.getPasswordExpired());
        r.tokenType=source.getTokenType(); r.roleList=source.getRoleList(); r.identityList=source.getIdentityList();
        return r;
    }
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getFormEmployeeId(){return formEmployeeId;} public void setFormEmployeeId(String v){formEmployeeId=v;}
    public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}
    public String getPersonKey(){return personKey;} public void setPersonKey(String v){personKey=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
    public String getAuthProvider(){return authProvider;} public void setAuthProvider(String v){authProvider=v;}
    public long getAuthVersion(){return authVersion;} public void setAuthVersion(long v){authVersion=v;}
    public boolean isAccountEnabled(){return accountEnabled;} public void setAccountEnabled(boolean v){accountEnabled=v;}
    public boolean isMustChangePassword(){return mustChangePassword;} public void setMustChangePassword(boolean v){mustChangePassword=v;}
    public String getReturnTo(){return returnTo;} public void setReturnTo(String v){returnTo=v;}
    public String getAudience(){return audience;} public void setAudience(String v){audience=v;}
    public String getMappingDecision(){return mappingDecision;} public void setMappingDecision(String v){mappingDecision=v;}
    public String getTokenType(){return tokenType;} public void setTokenType(String v){tokenType=v;}
    public List<String> getRoleList(){return roleList;} public void setRoleList(List<String> v){roleList=v==null?new ArrayList<>():v;}
    public List<AuthIdentityDto> getIdentityList(){return identityList;} public void setIdentityList(List<AuthIdentityDto> v){identityList=v==null?new ArrayList<>():v;}
}
