package com.x.cloudnext.server.auth;

import org.springframework.stereotype.Service;

@Service
public class O2AuthenticationProvider implements CloudNextAuthenticationProvider {
    private final O2AuthenticationClient client;
    public O2AuthenticationProvider(O2AuthenticationClient client){this.client=client;}
    @Override public String providerName(){return "O2OA";}
    @Override public AuthenticationResult verifyPassword(String credential,String password){
        return AuthenticationResult.fromO2(client.login(credential,password));
    }
}
