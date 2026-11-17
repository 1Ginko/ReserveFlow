package com.dataforge.authapi.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class JwtTestConfiguration {

    @Bean
    DynamicPropertyRegistrar jwtTestProperties() {
        return registry -> {
            registry.add("app.jwt.private-key-location", () -> "classpath:jwt/test-private.pem");
            registry.add("app.jwt.public-key-location", () -> "classpath:jwt/test-public.pem");
            registry.add("app.jwt.issuer", () -> "reserveflow-test-auth");
            registry.add("app.jwt.audience", () -> "reserveflow-test-api");
            registry.add("app.jwt.access-token-ttl", () -> "PT5M");
        };
    }
}
