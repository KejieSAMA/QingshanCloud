package com.x.cloudnext.server.auth;

import javax.validation.constraints.NotBlank;

public class SsoCallbackRequest {
    @NotBlank private String ticket;
    private String returnPath;
    public String getTicket(){return ticket;} public void setTicket(String v){ticket=v;}
    public String getReturnPath(){return returnPath;} public void setReturnPath(String v){returnPath=v;}
}
