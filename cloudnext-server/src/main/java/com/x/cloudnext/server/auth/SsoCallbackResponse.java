package com.x.cloudnext.server.auth;

public class SsoCallbackResponse {
    private String state;
    private com.x.cloudnext.server.namespace.DriveDescriptor drive;
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public com.x.cloudnext.server.namespace.DriveDescriptor getDrive() { return drive; }
    public void setDrive(com.x.cloudnext.server.namespace.DriveDescriptor v) { drive = v; }
    private String returnPath;
    public SsoCallbackResponse(){}
    public SsoCallbackResponse(String returnPath){this.returnPath=returnPath;}
    public String getReturnPath(){return returnPath;} public void setReturnPath(String v){returnPath=v;}
}
