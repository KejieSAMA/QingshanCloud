package com.x.cloudnext.server.drive;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.auth.O2AuthenticationResult;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;
import com.x.cloudnext.server.form.entity.CanonicalAlias;
import com.x.cloudnext.server.form.repository.CanonicalAliasRepository;

@TestPropertySource(properties = {"cloudnext.iteration.search.enabled=true"})
class CloudNextUnifiedSearchFlowTest extends AbstractDriveIntegrationTest {
    @Autowired private DriveShareRepository shares;
    @Autowired private CanonicalAliasRepository aliases;

    @Test void personUnitIdentityAliasesFindChildrenWithoutLeakingPrivatePath() throws Exception {
        shares.deleteAll(); aliases.deleteAll();
        AuthIdentityDto identity = new AuthIdentityDto(); identity.setId("canonical-identity"); identity.setUnit("canonical-unit");
        O2AuthenticationResult principal = new O2AuthenticationResult();
        principal.setPersonOaId(DEFAULT_OWNER_ID); principal.setName(DEFAULT_OWNER_NAME);
        principal.setIdentityList(Collections.singletonList(identity));
        String searchToken = authSessionService.createSession(principal).getToken();
        DriveItem privateParent = seedFolder("other-owner", "", "私密父目录");
        DriveItem root = seedFolder("other-owner", privateParent.getId(), "共享根");
        DriveItem child = seedFile("other-owner", root.getId(), "年度档案.pdf", "pdf", "application/pdf", new byte[]{1});
        for (DriveShareTargetType type : DriveShareTargetType.values()) {
            String canonical = type == DriveShareTargetType.PERSON ? DEFAULT_OWNER_ID : type == DriveShareTargetType.UNIT ? "canonical-unit" : "canonical-identity";
            CanonicalAlias alias = new CanonicalAlias(); alias.setId("alias-"+type); alias.setSubjectType(type.name());
            alias.setAliasKey("legacy-"+type); alias.setCanonicalKey(canonical); alias.setRelationType("MERGE"); aliases.saveAndFlush(alias);
            DriveShare share = new DriveShare(); share.setId("search-share-"+type); share.setItemId(root.getId());
            share.setOwnerPersonOaId("other-owner"); share.setOwnerName("Other"); share.setTargetType(type);
            share.setTargetId(alias.getAliasKey()); share.setTargetName("Target"); share.setPermission(DriveSharePermission.READ);
            shares.saveAndFlush(share);
            mockMvc.perform(get("/api/search").header(TOKEN_HEADER, searchToken).param("keyword", "档案"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(child.getId()))
                .andExpect(jsonPath("$.items[0].source").value("shared"))
                .andExpect(jsonPath("$.items[0].displayPath", not(containsString("私密"))))
                .andExpect(jsonPath("$.items[0].allowDownload").value(false));
            shares.deleteAll();
            mockMvc.perform(get("/api/search").header(TOKEN_HEADER, searchToken).param("keyword", "档案"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        }
    }
    @Test void unifiedSearchRequiresSessionAndValidInput() throws Exception {
        mockMvc.perform(get("/api/search").param("keyword", "档案")).andExpect(status().isUnauthorized());
        for (String keyword : new String[]{"", "x".repeat(101)}) {
            mockMvc.perform(get("/api/search").header(TOKEN_HEADER, token).param("keyword", keyword)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/search").header(TOKEN_HEADER, token).param("keyword", "档案").param("scope", "invalid"))
            .andExpect(status().isBadRequest());
    }
}
