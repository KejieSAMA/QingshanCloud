package com.x.cloudnext.server.form;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.auth.CloudNextAuthenticationProvider;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class FormAuthenticationProvider implements CloudNextAuthenticationProvider {
    private final FormIntegrationClient client;
    private final CanonicalBindingService bindings;
    private final FormLoginDirectorySync directorySync;
    private final CloudNextProperties properties;
    public FormAuthenticationProvider(FormIntegrationClient client, CanonicalBindingService bindings,
            FormLoginDirectorySync directorySync, CloudNextProperties properties) {
        this.client=client; this.bindings=bindings; this.directorySync=directorySync; this.properties=properties;
    }
    @Override public String providerName(){return "FORM";}
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public AuthenticationResult verifyPassword(String credential,String password){
        return bind(client.verifyPassword(credential,password));
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public AuthenticationResult exchangeTicket(String ticket){
        try {
            // The one-time ticket is exchanged exactly once. Retries below operate on this verified identity.
            AuthenticationResult principal=client.exchangeTicket(ticket);
            if(!properties.getAuth().getFormSsoAudience().equals(principal.getAudience()))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"form sso ticket audience is invalid");
            return bind(principal);
        } catch(ResponseStatusException failure) { throw FormSsoException.classify(failure); }
    }
    private AuthenticationResult bind(AuthenticationResult principal){
        if(!principal.isAccountEnabled()) throw new FormSsoException(HttpStatus.UNAUTHORIZED,"ACCOUNT_DISABLED","账号已停用，请联系管理员。");
        if(principal.isMustChangePassword()) throw new FormSsoException(HttpStatus.PRECONDITION_REQUIRED,"PASSWORD_CHANGE_REQUIRED","请先返回 OA 修改密码，再进入云盘。");
        directorySync.ensureBinding(principal.getTenantId(),principal.getFormEmployeeId());
        AuthenticationResult result=bindings.bindAuthentication(principal);
        if(!CanonicalBindingService.isLoginAllowed(result.getMappingDecision()))
            throw new FormSsoException(HttpStatus.FORBIDDEN,"IDENTITY_MAPPING_PENDING","身份映射待确认或已归档，请联系管理员核对。");
        return result;
    }
}
