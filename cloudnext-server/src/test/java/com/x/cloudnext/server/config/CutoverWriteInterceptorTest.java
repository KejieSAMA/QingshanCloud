package com.x.cloudnext.server.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

class CutoverWriteInterceptorTest {

    @Test
    void maintenanceAllowsOnlyTheExactArchiveExportGetPath() {
        CloudNextProperties properties = new CloudNextProperties();
        properties.getCutover().setMaintenanceMode(true);
        CutoverWriteInterceptor interceptor = new CutoverWriteInterceptor(properties,
                mock(CloudNextMetaRepository.class));

        assertTrue(interceptor.preHandle(request("GET", "/api/admin/form-migration/legacy/archive-export"),
                mock(HttpServletResponse.class), new Object()));
        assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request("GET", "/api/admin/form-migration/legacy/archive-export/extra"),
                        mock(HttpServletResponse.class), new Object()));
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn("");
        return request;
    }
}
