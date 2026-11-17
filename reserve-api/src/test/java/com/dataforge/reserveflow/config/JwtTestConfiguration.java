package com.dataforge.reserveflow.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration(proxyBeanMethods = false)
public class JwtTestConfiguration {

    @Bean
    DynamicPropertyRegistrar jwtTestProperties() {
        return registry -> {
            registry.add("app.jwt.public-key-location", () -> "classpath:jwt/test-public.pem");
            registry.add("app.jwt.issuer", () -> "reserveflow-test-auth");
            registry.add("app.jwt.audience", () -> "reserveflow-test-api");
        };
    }

    @Bean
    KeyPair jwtTestKeyPair() throws IOException {
        try (
                InputStream privateKeyStream = new ClassPathResource("jwt/test-private.pem").getInputStream();
                InputStream publicKeyStream = new ClassPathResource("jwt/test-public.pem").getInputStream()
        ) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateKeyStream);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicKeyStream);
            return new KeyPair(publicKey, privateKey);
        }
    }

    @Bean
    JwtEncoder jwtTestEncoder(KeyPair jwtTestKeyPair) {
        return NimbusJwtEncoder.withKeyPair(
                        (RSAPublicKey) jwtTestKeyPair.getPublic(),
                        (RSAPrivateKey) jwtTestKeyPair.getPrivate()
                )
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }
}
