package com.x.cloudnext.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.x.cloudnext.server.config.CloudNextProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CloudNextProperties.class)
public class CloudNextServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudNextServerApplication.class, args);
    }
}
