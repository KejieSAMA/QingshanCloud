package com.x.cloudnext.server.drive;

public class OrgSearchResultResponse {

    private String type;
    private String id;
    private String name;
    private String distinguishedName;
    private String path;
    private String key;
    private String personKey;
    private String unitKey;
    private String identityKey;
    private String displayPath;
    private String employeeNo;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    public String getKey(){return key;} public void setKey(String v){key=v;}
    public String getPersonKey(){return personKey;} public void setPersonKey(String v){personKey=v;}
    public String getUnitKey(){return unitKey;} public void setUnitKey(String v){unitKey=v;}
    public String getIdentityKey(){return identityKey;} public void setIdentityKey(String v){identityKey=v;}
    public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
    public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}
}
