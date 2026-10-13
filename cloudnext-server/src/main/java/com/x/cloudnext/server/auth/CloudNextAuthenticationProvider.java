package com.x.cloudnext.server.auth;

public interface CloudNextAuthenticationProvider {
    String providerName();
    AuthenticationResult verifyPassword(String credential,String password);
}
