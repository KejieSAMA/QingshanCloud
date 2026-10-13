package com.x.cloudnext.server.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MigrationValidationResponse {
    private boolean ready;private List<String> blockers=new ArrayList<>();private Map<String,Long> counts=new LinkedHashMap<>();
    public boolean isReady(){return ready;} public void setReady(boolean v){ready=v;}
    public List<String> getBlockers(){return blockers;} public void setBlockers(List<String> v){blockers=v;}
    public Map<String,Long> getCounts(){return counts;} public void setCounts(Map<String,Long> v){counts=v;}
}
