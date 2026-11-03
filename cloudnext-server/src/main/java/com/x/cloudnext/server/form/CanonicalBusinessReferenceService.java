package com.x.cloudnext.server.form;

import java.time.Instant;

import org.springframework.stereotype.Service;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.DriveShareTargetType;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;
import com.x.cloudnext.server.workspace.repository.UserPreferenceRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class CanonicalBusinessReferenceService {
    @javax.persistence.PersistenceContext private javax.persistence.EntityManager entityManager;
    private final DriveItemRepository items;
    private final DriveShareRepository shares;
    private final DrivePublicLinkRepository links;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserPreferenceRepository preferences;

    public CanonicalBusinessReferenceService(DriveItemRepository items,DriveShareRepository shares,DrivePublicLinkRepository links,
            WorkspaceRepository workspaces,WorkspaceMemberRepository members,UserPreferenceRepository preferences){
        this.items=items;this.shares=shares;this.links=links;this.workspaces=workspaces;this.members=members;this.preferences=preferences;
    }

    public long principalReferences(String key){
        if(key==null)return 0;
        if (com.x.cloudnext.server.namespace.DriveContext.current() != null) {
            // Canonical identity guards must see references in every drive. Only aggregate counts are read;
            // no unscoped business entities or file contents are returned to a user request.
            return globalCount("SELECT COUNT(*) FROM cloudnext_drive_item WHERE owner_person_oa_id=:key OR (scope_type='PERSONAL' AND scope_id=:key)", key)
                + globalActiveCount("cloudnext_drive_share", "owner_person_oa_id=:key OR (target_type='PERSON' AND target_id=:key)", key)
                + globalActiveCount("cloudnext_drive_public_link", "creator_person_oa_id=:key", key)
                + globalCount("SELECT COUNT(*) FROM cloudnext_workspace_member WHERE person_oa_id=:key", key)
                + globalCount("SELECT COUNT(*) FROM cloudnext_user_preference WHERE person_oa_id=:key", key);
        }
        return items.findAll().stream().filter(i->key.equals(i.getOwnerPersonOaId())||(i.getScopeType()==DriveScopeType.PERSONAL&&key.equals(i.getScopeId()))).count()
                +shares.findAll().stream().filter(this::activeShare).filter(s->key.equals(s.getOwnerPersonOaId())||(s.getTargetType()==DriveShareTargetType.PERSON&&key.equals(s.getTargetId()))).count()
                +links.findAll().stream().filter(l->!l.isRevoked()&&notExpired(l.getExpiresAt())).filter(l->key.equals(l.getCreatorPersonOaId())).count()
                +members.findByPersonOaId(key).size()
                +preferences.findAll().stream().filter(p->key.equals(p.getPersonOaId())).count();
    }

    public long identityReferences(String key){
        if (key != null && com.x.cloudnext.server.namespace.DriveContext.current() != null) {
            return globalActiveCount("cloudnext_drive_share", "target_type='IDENTITY' AND target_id=:key", key);
        }
        return key==null?0:shares.findAll().stream().filter(this::activeShare).filter(s->s.getTargetType()==DriveShareTargetType.IDENTITY&&key.equals(s.getTargetId())).count();
    }
    private long globalCount(String query, String key) { return ((Number) entityManager.createNativeQuery(query).setParameter("key", key).getSingleResult()).longValue(); }
    private long globalActiveCount(String table, String predicate, String key) {
        java.util.List<?> expirations = entityManager.createNativeQuery("SELECT expires_at FROM " + table + " WHERE revoked=0 AND (" + predicate + ")").setParameter("key", key).getResultList();
        return expirations.stream().filter(value -> notExpired(value == null ? null : value.toString())).count();
    }
    private boolean activeShare(com.x.cloudnext.server.drive.entity.DriveShare share){return !share.isRevoked()&&notExpired(share.getExpiresAt());}
    private boolean notExpired(String expiresAt){if(expiresAt==null||expiresAt.trim().isEmpty())return true;try{return Instant.parse(expiresAt).isAfter(Instant.now());}catch(Exception e){return false;}}
}
