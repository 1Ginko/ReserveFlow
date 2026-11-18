package com.dataforge.reserveflow.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import static com.dataforge.reserveflow.security.UserRole.ADMIN;
import static com.dataforge.reserveflow.security.UserRole.USER;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/*"
                        ).hasAnyRole(USER.name(), ADMIN.name())

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products",
                                "/api/products/*/inventory/restock"
                        ).hasRole(ADMIN.name())

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/*"
                        ).hasRole(ADMIN.name())

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reservations/*"
                        ).hasAnyRole(USER.name(), ADMIN.name())

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/reservations",
                                "/api/reservations/*/confirm",
                                "/api/reservations/*/cancel"
                        ).hasAnyRole(USER.name(), ADMIN.name())

                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return authenticationConverter;
    }
}
