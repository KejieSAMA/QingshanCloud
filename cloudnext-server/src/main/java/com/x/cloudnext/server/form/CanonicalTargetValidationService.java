package com.x.cloudnext.server.form;

import java.util.Collections;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.drive.DriveShareTargetType;import com.x.cloudnext.server.form.entity.IdentityBinding;import com.x.cloudnext.server.form.repository.*;

@Service
public class CanonicalTargetValidationService {
 private final PrincipalBindingRepository people;private final UnitBindingRepository units;private final IdentityBindingRepository identities;private final CanonicalAliasResolver aliases;
 public CanonicalTargetValidationService(PrincipalBindingRepository people,UnitBindingRepository units,IdentityBindingRepository identities,CanonicalAliasResolver aliases){this.people=people;this.units=units;this.identities=identities;this.aliases=aliases;}
 public void requireResolved(DriveShareTargetType type,String key){boolean valid;switch(type){case PERSON:valid=people.findAll().stream().anyMatch(p->keyMatches("PERSON",key,p.getCanonicalPersonId())&&p.isAccountEnabled()&&"ACTIVE".equals(p.getStatus())&&CanonicalBindingService.isLoginAllowed(p.getMigrationDecision()));break;case UNIT:valid=units.findAll().stream().anyMatch(u->keyMatches("UNIT",key,u.getCanonicalUnitId())&&"ACTIVE".equals(u.getStatus())&&CanonicalBindingService.isLoginAllowed(u.getMigrationDecision()));break;case IDENTITY:valid=identities.findAll().stream().filter(i->keyMatches("IDENTITY",key,i.getCanonicalIdentityId())).anyMatch(this::activeIdentity);break;default:valid=false;}if(!valid)throw new ResponseStatusException(HttpStatus.CONFLICT,"share or workspace target mapping is pending, inactive, or unknown");}
 public void requirePerson(String key){requireResolved(DriveShareTargetType.PERSON,key);}
 private boolean activeIdentity(IdentityBinding i){return "ACTIVE".equals(i.getStatus())&&CanonicalBindingService.isLoginAllowed(i.getMigrationDecision())&&units.findByCanonicalUnitId(i.getCanonicalUnitId()).stream().anyMatch(u->"ACTIVE".equals(u.getStatus())&&CanonicalBindingService.isLoginAllowed(u.getMigrationDecision()));}
 private boolean keyMatches(String type,String requested,String canonical){return canonical.equals(requested)||aliases.includeLegacyAliases(type,Collections.singleton(canonical)).contains(requested);}
}
