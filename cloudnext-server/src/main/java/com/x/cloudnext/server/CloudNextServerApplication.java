package com.x.cloudnext.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.x.cloudnext.server.config.CloudNextProperties;

@SpringBootApplication
@EnableConfigurationProperties(CloudNextProperties.class)
public class CloudNextServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudNextServerApplication.class, args);
    }
}
