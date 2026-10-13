package com.x.cloudnext.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.x.cloudnext.server.auth.AuthSessionInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthSessionInterceptor authSessionInterceptor;
    private final CutoverWriteInterceptor cutoverWriteInterceptor;

    public WebMvcConfig(AuthSessionInterceptor authSessionInterceptor,CutoverWriteInterceptor cutoverWriteInterceptor) {
        this.authSessionInterceptor = authSessionInterceptor;
        this.cutoverWriteInterceptor = cutoverWriteInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cutoverWriteInterceptor).addPathPatterns("/api/**","/public/**");
        registry.addInterceptor(authSessionInterceptor)
                .addPathPatterns("/api/**")
                // Public link endpoints are anonymous by design (Requirement 1.7 / 8.x);
                // they live under /public/** so the AuthSessionInterceptor does not
                // intercept them today, but we keep an explicit pattern for the
                // future case where we widen the addPathPatterns scope. The
                // exclusion is path-level and intentionally applies regardless of
                // cloudnext.iteration.public-links.enabled — otherwise toggling the
                // flag on would cause requests to be rejected with 401 before they
                // could ever reach CloudNextPublicLinkController.
                .excludePathPatterns(
                        "/api/auth/status",
                        "/api/auth/login",
                        "/api/auth/sso/**",
                        "/api/onlyoffice/**",
                        "/public/links/**");
    }
}
