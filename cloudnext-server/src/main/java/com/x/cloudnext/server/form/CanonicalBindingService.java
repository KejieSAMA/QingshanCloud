package com.x.cloudnext.server.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.form.entity.IdentityBinding;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.repository.IdentityBindingRepository;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;
import com.x.cloudnext.server.form.repository.UnitBindingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CanonicalBindingService {
    private final PrincipalBindingRepository principals;
    private final IdentityBindingRepository identities;
    private final ObjectMapper objectMapper;
    private final UnitBindingRepository units;
    public CanonicalBindingService(PrincipalBindingRepository principals,IdentityBindingRepository identities,ObjectMapper objectMapper,UnitBindingRepository units){
        this.principals=principals;this.identities=identities;this.objectMapper=objectMapper;this.units=units;
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AuthenticationResult bindAuthentication(AuthenticationResult remote){
        PrincipalBinding binding=principals.findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM",remote.getTenantId(),remote.getFormEmployeeId())
                .orElseThrow(()->new FormSsoException(HttpStatus.CONFLICT,"DIRECTORY_BINDING_MISSING","人员同步完成后仍未找到绑定，请联系管理员核对目录。"));
        if(!binding.isAccountEnabled() || !"ACTIVE".equals(binding.getStatus()))
            throw new FormSsoException(HttpStatus.FORBIDDEN,"IDENTITY_UNAVAILABLE","云盘身份已停用或归档，请联系管理员核对。");
        if(remote.getAuthVersion()<binding.getAuthVersion())
            throw new FormSsoException(HttpStatus.UNAUTHORIZED,"TICKET_INVALID","身份信息已更新，请重新免登。");
        binding.setEmployeeNoSnapshot(remote.getEmployeeNo()); binding.setDisplayName(remote.getName()); if(StringUtils.hasText(remote.getDisplayPath()))binding.setDisplayPath(remote.getDisplayPath());
        binding.setSourceAccountEnabled(remote.isAccountEnabled());binding.setDirectoryStatus(remote.isAccountEnabled()?"ACTIVE":"DISABLED");binding.setAuthVersion(remote.getAuthVersion());
        if(!isTerminal(binding.getMigrationDecision())){binding.setAccountEnabled(remote.isAccountEnabled());binding.setStatus(binding.getDirectoryStatus());}
        binding.setRoleListJson(writeRoles(remote.getRoleList())); principals.save(binding);
        remote.setMappingDecision(binding.getMigrationDecision());remote.setPersonKey(binding.getCanonicalPersonId());remote.setDisplayPath(binding.getDisplayPath()); remote.setIdentityList(toAuthIdentities(binding.getCanonicalPersonId()));
        return remote;
    }

    @Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean hasBinding(String tenantId,String employeeId){
        return principals.existsByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM",tenantId,employeeId);
    }

    @Transactional(readOnly=true)
    public boolean sessionSubjectIsActive(String tenantId,String employeeId,String canonicalPersonId,long authVersion){
        if(!StringUtils.hasText(tenantId)||!StringUtils.hasText(employeeId)) return true;
        return principals.findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM",tenantId,employeeId)
                .map(p->p.isAccountEnabled()&&"ACTIVE".equals(p.getStatus())&&isLoginAllowed(p.getMigrationDecision())&&canonicalPersonId.equals(p.getCanonicalPersonId())&&authVersion==p.getAuthVersion()).orElse(false);
    }
    public static boolean isTerminal(String decision){return "TOMBSTONE".equals(decision)||"ARCHIVE".equals(decision)||"REVOKED".equals(decision)||"TRANSFERRED".equals(decision);}
    public static boolean isLoginAllowed(String decision){return StringUtils.hasText(decision)&&!"PENDING".equals(decision)&&!isTerminal(decision);}
    @Transactional(readOnly=true) public void refreshAuthorization(AuthLoginResponse response,String tenantId,String employeeId){principals.findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM",tenantId,employeeId).ifPresent(p->{response.setRoleList(roles(p));response.setIdentityList(toAuthIdentities(p.getCanonicalPersonId()));response.setEmployeeNo(p.getEmployeeNoSnapshot());response.setPersonName(p.getDisplayName());response.setDisplayPath(p.getDisplayPath());response.setPersonDistinguishedName(p.getDisplayPath());});}
    private List<AuthIdentityDto> toAuthIdentities(String personKey){return identities.findByCanonicalPersonIdAndStatus(personKey,"ACTIVE").stream().filter(i->isLoginAllowed(i.getMigrationDecision())).filter(i->units.findByCanonicalUnitId(i.getCanonicalUnitId()).stream().anyMatch(u->"ACTIVE".equals(u.getStatus())&&isLoginAllowed(u.getMigrationDecision()))).map(this::toDto).collect(Collectors.toList());}
    private AuthIdentityDto toDto(IdentityBinding b){AuthIdentityDto d=new AuthIdentityDto();d.setIdentityKey(b.getCanonicalIdentityId());d.setId(b.getCanonicalIdentityId());d.setUnitKey(b.getCanonicalUnitId());d.setUnit(b.getCanonicalUnitId());d.setName(b.getIdentityName());d.setDistinguishedName(b.getDisplayPath());d.setDisplayPath(b.getDisplayPath());return d;}
    private String writeRoles(List<String> roles){try{return objectMapper.writeValueAsString(roles==null?new ArrayList<>():roles);}catch(Exception e){return "[]";}}
    @Transactional(readOnly=true) public List<String> roles(PrincipalBinding p){try{return objectMapper.readValue(p.getRoleListJson(),new TypeReference<List<String>>(){});}catch(Exception e){return Collections.emptyList();}}
}
