package com.dataforge.authapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${app.jwt.private-key-location}") Resource privateKeyResource,
            @Value("${app.jwt.public-key-location}") Resource publicKeyResource
    ) throws IOException {
        try (
                InputStream privateKeyStream = privateKeyResource.getInputStream();
                InputStream publicKeyStream = publicKeyResource.getInputStream()
        ) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateKeyStream);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicKeyStream);
            return NimbusJwtEncoder
                    .withKeyPair(publicKey, privateKey)
                    .algorithm(SignatureAlgorithm.RS256)
                    .build();
        }
    }
}