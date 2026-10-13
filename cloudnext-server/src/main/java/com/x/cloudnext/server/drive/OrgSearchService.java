package com.x.cloudnext.server.drive;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.orgsync.entity.OrgIdentityMirror;
import com.x.cloudnext.server.orgsync.entity.OrgPersonMirror;
import com.x.cloudnext.server.orgsync.entity.OrgUnitMirror;
import com.x.cloudnext.server.orgsync.repository.OrgIdentityMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgPersonMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgUnitMirrorRepository;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.entity.UnitBinding;
import com.x.cloudnext.server.form.entity.IdentityBinding;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;
import com.x.cloudnext.server.form.repository.UnitBindingRepository;
import com.x.cloudnext.server.form.repository.IdentityBindingRepository;
import com.x.cloudnext.server.form.CanonicalBindingService;

@Service
public class OrgSearchService {

    private static final int RESULT_LIMIT = 20;

    private final OrgPersonMirrorRepository orgPersonMirrorRepository;
    private final OrgUnitMirrorRepository orgUnitMirrorRepository;
    private final OrgIdentityMirrorRepository orgIdentityMirrorRepository;
    private final PrincipalBindingRepository principalBindingRepository;
    private final UnitBindingRepository unitBindingRepository;
    private final IdentityBindingRepository identityBindingRepository;

    public OrgSearchService(
            OrgPersonMirrorRepository orgPersonMirrorRepository,
            OrgUnitMirrorRepository orgUnitMirrorRepository,
            OrgIdentityMirrorRepository orgIdentityMirrorRepository,
            PrincipalBindingRepository principalBindingRepository,
            UnitBindingRepository unitBindingRepository,
            IdentityBindingRepository identityBindingRepository) {
        this.orgPersonMirrorRepository = orgPersonMirrorRepository;
        this.orgUnitMirrorRepository = orgUnitMirrorRepository;
        this.orgIdentityMirrorRepository = orgIdentityMirrorRepository;
        this.principalBindingRepository = principalBindingRepository;
        this.unitBindingRepository = unitBindingRepository;
        this.identityBindingRepository = identityBindingRepository;
    }

    public List<OrgSearchResultResponse> search(String type, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return Collections.emptyList();
        }
        DriveShareTargetType targetType = DriveShareTargetType.fromValue(type);
        switch (targetType) {
            case UNIT:
                if (unitBindingRepository.count() > 0) {
                    return unitBindingRepository.findAll().stream().filter(item -> "ACTIVE".equals(item.getStatus()) && CanonicalBindingService.isLoginAllowed(item.getMigrationDecision()))
                            .filter(item -> matches(normalizedKeyword,item.getUnitName(),item.getDisplayPath()))
                            .limit(RESULT_LIMIT).map(this::toUnitResult).collect(Collectors.toList());
                }
                return orgUnitMirrorRepository.findAll().stream()
                        .filter(item -> matches(normalizedKeyword, item.getName(), item.getUniqueName(), item.getDistinguishedName()))
                        .limit(RESULT_LIMIT)
                        .map(item -> toResult("unit", item.getOaId(), item.getName(), item.getDistinguishedName(), item.getLevelName()))
                        .collect(Collectors.toList());
            case IDENTITY:
                if (identityBindingRepository.count() > 0) {
                    return identityBindingRepository.findAll().stream().filter(item -> "ACTIVE".equals(item.getStatus()) && CanonicalBindingService.isLoginAllowed(item.getMigrationDecision()))
                            .filter(item -> matches(normalizedKeyword,item.getIdentityName(),item.getDisplayPath()))
                            .limit(RESULT_LIMIT).map(this::toIdentityResult).collect(Collectors.toList());
                }
                return orgIdentityMirrorRepository.findAll().stream()
                        .filter(item -> matches(normalizedKeyword, item.getName(), item.getUniqueName(), item.getDistinguishedName(), item.getUnitName()))
                        .limit(RESULT_LIMIT)
                        .map(item -> toResult("identity", item.getOaId(), item.getName(), item.getDistinguishedName(), item.getUnitLevelName()))
                        .collect(Collectors.toList());
            case PERSON:
            default:
                if (principalBindingRepository.count() > 0) {
                    return principalBindingRepository.findAll().stream().filter(item -> item.isAccountEnabled() && "ACTIVE".equals(item.getStatus()) && CanonicalBindingService.isLoginAllowed(item.getMigrationDecision()))
                            .filter(item -> matches(normalizedKeyword,item.getDisplayName(),item.getEmployeeNoSnapshot(),item.getDisplayPath()))
                            .limit(RESULT_LIMIT).map(this::toPersonResult).collect(Collectors.toList());
                }
                return orgPersonMirrorRepository.findAll().stream()
                        .filter(item -> matches(normalizedKeyword, item.getName(), item.getUniqueName(), item.getDistinguishedName(), item.getMail()))
                        .limit(RESULT_LIMIT)
                        .map(item -> toResult("person", item.getOaId(), item.getName(), item.getDistinguishedName(), null))
                        .collect(Collectors.toList());
        }
    }

    private OrgSearchResultResponse toResult(String type, String id, String name, String distinguishedName, String path) {
        OrgSearchResultResponse response = new OrgSearchResultResponse();
        response.setType(type);
        response.setId(id);
        response.setName(name);
        response.setDistinguishedName(distinguishedName);
        response.setPath(path);
        response.setKey(id);
        response.setDisplayPath(StringUtils.hasText(path) ? path : distinguishedName);
        if ("person".equals(type)) response.setPersonKey(id);
        if ("unit".equals(type)) response.setUnitKey(id);
        if ("identity".equals(type)) response.setIdentityKey(id);
        return response;
    }

    private OrgSearchResultResponse toPersonResult(PrincipalBinding item){OrgSearchResultResponse response=toResult("person",item.getCanonicalPersonId(),item.getDisplayName(),item.getDisplayPath(),item.getDisplayPath());response.setEmployeeNo(item.getEmployeeNoSnapshot());return response;}
    private OrgSearchResultResponse toUnitResult(UnitBinding item){return toResult("unit",item.getCanonicalUnitId(),item.getUnitName(),item.getDisplayPath(),item.getDisplayPath());}
    private OrgSearchResultResponse toIdentityResult(IdentityBinding item){return toResult("identity",item.getCanonicalIdentityId(),item.getIdentityName(),item.getDisplayPath(),item.getDisplayPath());}

    private boolean matches(String keyword, String... values) {
        return Stream.of(values)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(keyword));
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
    }
}
