package com.x.cloudnext.server.form;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;

public class MappingDecisionRequest {
    private String tenantId;
    @NotBlank private String decision;
    private String canonicalKey;
    private String legacyOaId;
    private List<String> aliases=new ArrayList<>();
    private String note;
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getDecision(){return decision;} public void setDecision(String v){decision=v;}
    public String getCanonicalKey(){return canonicalKey;} public void setCanonicalKey(String v){canonicalKey=v;}
    public String getLegacyOaId(){return legacyOaId;} public void setLegacyOaId(String v){legacyOaId=v;}
    public List<String> getAliases(){return aliases;} public void setAliases(List<String> v){aliases=v==null?new ArrayList<>():v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
}
