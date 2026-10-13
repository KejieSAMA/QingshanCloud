package com.x.cloudnext.server.auth;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class AuthenticationProviderRouter {
    private final CloudNextProperties properties;
    private final List<CloudNextAuthenticationProvider> providers;
    public AuthenticationProviderRouter(CloudNextProperties properties,List<CloudNextAuthenticationProvider> providers){
        this.properties=properties;this.providers=providers;
    }
    public AuthenticationResult verifyPassword(String credential,String password){
        String configured=properties.getAuth().getEffectiveProvider();
        for(CloudNextAuthenticationProvider provider:providers){
            if(provider.providerName().equalsIgnoreCase(configured)) return provider.verifyPassword(credential,password);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"cloudnext auth provider is not available");
    }
}
