package com.dataforge.reserveflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.jwt.public-key-location}") Resource publicKeyResource,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            Clock clock
    ) throws IOException {
        try (InputStream stream = publicKeyResource.getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(stream);

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();

            JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
            timestampValidator.setClock(clock);
            timestampValidator.setAllowEmptyExpiryClaim(false);

            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    timestampValidator,
                    new JwtIssuerValidator(issuer),
                    new JwtAudienceValidator(audience)
            ));

            return decoder;
        }
    }
}