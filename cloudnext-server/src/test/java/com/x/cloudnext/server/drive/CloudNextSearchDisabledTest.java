package com.x.cloudnext.server.drive;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

// N+2.7: 自 SearchController 改为 matchIfMissing=true 后，缺省即开启；本测试显式
// 把 iteration flag 设为 false 校验"禁用语义"仍然有效（404）。
@TestPropertySource(properties = "cloudnext.iteration.search.enabled=false")
class CloudNextSearchDisabledTest extends AbstractDriveIntegrationTest {

    @Test
    void searchEndpointIsHiddenWhenIterationFlagIsOff() throws Exception {
        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "personal")
                .param("keyword", "plan"))
                .andExpect(status().isNotFound());
    }
}
