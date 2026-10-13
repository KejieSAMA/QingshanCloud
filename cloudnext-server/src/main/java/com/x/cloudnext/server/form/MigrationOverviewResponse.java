package com.x.cloudnext.server.form;

import java.util.LinkedHashMap;
import java.util.Map;

public class MigrationOverviewResponse {
    private Map<String,Long> principalCounts=new LinkedHashMap<>();
    private Map<String,Long> unitCounts=new LinkedHashMap<>();
    private Map<String,Long> identityCounts=new LinkedHashMap<>();
    private String syncOutcome;private String syncEtag;private String lastSuccessAt;private boolean ready;private long blockerCount;
    public Map<String,Long> getPrincipalCounts(){return principalCounts;} public void setPrincipalCounts(Map<String,Long> v){principalCounts=v;}
    public Map<String,Long> getUnitCounts(){return unitCounts;} public void setUnitCounts(Map<String,Long> v){unitCounts=v;}
    public Map<String,Long> getIdentityCounts(){return identityCounts;} public void setIdentityCounts(Map<String,Long> v){identityCounts=v;}
    public String getSyncOutcome(){return syncOutcome;} public void setSyncOutcome(String v){syncOutcome=v;}
    public String getSyncEtag(){return syncEtag;} public void setSyncEtag(String v){syncEtag=v;}
    public String getLastSuccessAt(){return lastSuccessAt;} public void setLastSuccessAt(String v){lastSuccessAt=v;}
    public boolean isReady(){return ready;} public void setReady(boolean v){ready=v;}
    public long getBlockerCount(){return blockerCount;} public void setBlockerCount(long v){blockerCount=v;}
}
