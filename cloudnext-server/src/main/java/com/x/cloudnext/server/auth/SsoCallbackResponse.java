package com.x.cloudnext.server.auth;

public class SsoCallbackResponse {
    private String returnPath;
    public SsoCallbackResponse(){}
    public SsoCallbackResponse(String returnPath){this.returnPath=returnPath;}
    public String getReturnPath(){return returnPath;} public void setReturnPath(String v){returnPath=v;}
}
