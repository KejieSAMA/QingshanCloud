package com.x.cloudnext.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tests and database maintenance must not run production jobs. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "cloudnext.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CloudNextSchedulingConfig {
}
