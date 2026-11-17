package com.dataforge.reserveflow.config;

import com.dataforge.reserveflow.products.ProductService;
import com.dataforge.reserveflow.products.api.ProductController;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;
import com.dataforge.reserveflow.reservations.ReservationService;
import com.dataforge.reserveflow.reservations.api.ReservationController;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.dataforge.reserveflow.security.UserRole.ADMIN;
import static com.dataforge.reserveflow.security.UserRole.USER;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProductController.class, ReservationController.class})
@Import({SecurityConfig.class, JwtConfig.class, JwtTestConfiguration.class})
class SecurityConfigTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final UUID RESERVATION_ID = UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("8fa9556c-2c2f-4b34-9af5-f9f020aa9a15");
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final String ISSUER = "reserveflow-test-auth";
    private static final String AUDIENCE = "reserveflow-test-api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtTestEncoder;

    @Autowired
    private KeyPair jwtTestKeyPair;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
    }

    @ParameterizedTest
    @MethodSource("protectedRequests")
    void requiresTokenForEveryEndpoint(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));

        verifyNoInteractions(productService, reservationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokenClaims")
    void rejectsInvalidTokenClaims(String description, JwtClaimsSet claims) throws Exception {
        String token = sign(jwtTestEncoder, SignatureAlgorithm.RS256, claims);

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, reservationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "eyJhbGciOiJub25lIn0.e30."})
    void rejectsMalformedOrUnsignedToken(String token) throws Exception {
        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void rejectsTokenSignedByDifferentKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();
        JwtEncoder otherEncoder = NimbusJwtEncoder.withKeyPair(
                        (RSAPublicKey) otherKeyPair.getPublic(),
                        (RSAPrivateKey) otherKeyPair.getPrivate()
                )
                .algorithm(SignatureAlgorithm.RS256)
                .build();
        String token = sign(otherEncoder, SignatureAlgorithm.RS256, claims(USER.name()).build());

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void rejectsTokenUsingUnexpectedSignatureAlgorithm() throws Exception {
        JwtEncoder otherEncoder = NimbusJwtEncoder.withKeyPair(
                        (RSAPublicKey) jwtTestKeyPair.getPublic(),
                        (RSAPrivateKey) jwtTestKeyPair.getPrivate()
                )
                .algorithm(SignatureAlgorithm.RS512)
                .build();
        String token = sign(otherEncoder, SignatureAlgorithm.RS512, claims(USER.name()).build());

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void rejectsRoleChangedWithoutResigningToken() throws Exception {
        String[] parts = token(USER).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String modifiedPayload = payload.replace("\"USER\"", "\"ADMIN\"");
        assertNotEquals(payload, modifiedPayload);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modifiedPayload.getBytes(StandardCharsets.UTF_8));
        String modifiedToken = String.join(".", parts[0], encodedPayload, parts[2]);

        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, "Bearer " + modifiedToken)
                        .contentType(APPLICATION_JSON)
                        .content(createProductJson()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, reservationService);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanReadCatalog(UserRole role) throws Exception {
        Product product = product();
        ProductListQuery query = new ProductListQuery(0, 20, null);
        given(productService.findPage(query))
                .willReturn(new ProductPage(List.of(product), 0, 20, 1));
        given(productService.findById(PRODUCT_ID, false)).willReturn(product);
        String token = token(role);

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()));

        mockMvc.perform(get("/api/products/{productId}", PRODUCT_ID)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));

        then(productService).should().findPage(query);
        then(productService).should().findById(PRODUCT_ID, false);
    }

    @ParameterizedTest
    @MethodSource("productWriteRequests")
    void userCannotChangeProductsOrInventory(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + token(USER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        CreateProductCommand command = new CreateProductCommand(
                "PHONE-001", "Smartphone", "Test product", new BigDecimal("799.99"), "USD", 10
        );
        given(productService.create(command)).willReturn(product());

        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token(ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(createProductJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));

        then(productService).should().create(command);
    }

    @Test
    void adminCanUpdateProduct() throws Exception {
        UpdateProductCommand command = new UpdateProductCommand(
                PRODUCT_ID, "Smartphone Pro", "Updated product", new BigDecimal("899.99"), "USD", true, 0
        );
        given(productService.update(command)).willReturn(product());

        mockMvc.perform(put("/api/products/{productId}", PRODUCT_ID)
                        .header(AUTHORIZATION, "Bearer " + token(ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(updateProductJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));

        then(productService).should().update(command);
    }

    @Test
    void adminCanRestockInventory() throws Exception {
        RestockProductCommand command = new RestockProductCommand(PRODUCT_ID, 5, 0);
        given(productService.restock(command)).willReturn(new ProductInventory(15, 0, 1, NOW));

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", PRODUCT_ID)
                        .header(AUTHORIZATION, "Bearer " + token(ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(restockJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(15));

        then(productService).should().restock(command);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanCreateReservation(UserRole role) throws Exception {
        CreateReservationCommand command = new CreateReservationCommand(PRODUCT_ID, IDEMPOTENCY_KEY, 3);
        given(reservationService.create(command))
                .willReturn(new ReservationCreationResult(reservation(), true));

        mockMvc.perform(post("/api/reservations")
                        .header(AUTHORIZATION, "Bearer " + token(role))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
                        .contentType(APPLICATION_JSON)
                        .content(createReservationJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()));

        then(reservationService).should().create(command);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanReadReservation(UserRole role) throws Exception {
        given(reservationService.findById(RESERVATION_ID)).willReturn(reservation());

        mockMvc.perform(get("/api/reservations/{reservationId}", RESERVATION_ID)
                        .header(AUTHORIZATION, "Bearer " + token(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()));

        then(reservationService).should().findById(RESERVATION_ID);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanConfirmReservation(UserRole role) throws Exception {
        mockMvc.perform(post("/api/reservations/{reservationId}/confirm", RESERVATION_ID)
                        .header(AUTHORIZATION, "Bearer " + token(role)))
                .andExpect(status().isNoContent());

        then(reservationService).should().confirm(RESERVATION_ID);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanCancelReservation(UserRole role) throws Exception {
        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", RESERVATION_ID)
                        .header(AUTHORIZATION, "Bearer " + token(role)))
                .andExpect(status().isNoContent());

        then(reservationService).should().cancel(RESERVATION_ID);
    }

    @Test
    void rejectsValidTokenWithoutRole() throws Exception {
        JwtClaimsSet claims = claims(USER.name()).claims(values -> values.remove("role")).build();
        String token = sign(jwtTestEncoder, SignatureAlgorithm.RS256, claims);

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void rejectsValidTokenWithUnsupportedRole() throws Exception {
        String token = sign(jwtTestEncoder, SignatureAlgorithm.RS256, claims("SUPERVISOR").build());

        mockMvc.perform(get("/api/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void deniesUnlistedEndpointEvenForAdmin() throws Exception {
        mockMvc.perform(get("/api/unlisted")
                        .header(AUTHORIZATION, "Bearer " + token(ADMIN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService, reservationService);
    }

    @Test
    void doesNotReuseAuthenticationFromHttpSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ProductListQuery query = new ProductListQuery(0, 20, null);
        given(productService.findPage(query)).willReturn(new ProductPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/products")
                        .session(session)
                        .header(AUTHORIZATION, "Bearer " + token(USER)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products").session(session))
                .andExpect(status().isUnauthorized());

        then(productService).should().findPage(query);
    }

    private String token(UserRole role) {
        return sign(jwtTestEncoder, SignatureAlgorithm.RS256, claims(role.name()).build());
    }

    private static String sign(JwtEncoder encoder, SignatureAlgorithm algorithm, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(algorithm).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtClaimsSet.Builder claims(String role) {
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(USER_ID.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(300))
                .claim("role", role);
    }

    private static Stream<Arguments> invalidTokenClaims() {
        return Stream.of(
                Arguments.of("Expired token", claims(USER.name())
                        .issuedAt(NOW.minusSeconds(600))
                        .expiresAt(NOW.minusSeconds(120)).build()),
                Arguments.of("Token not valid yet", claims(USER.name())
                        .notBefore(NOW.plusSeconds(120)).build()),
                Arguments.of("Missing expiry", claims(USER.name())
                        .claims(values -> values.remove("exp")).build()),
                Arguments.of("Wrong issuer", claims(USER.name()).issuer("other-auth").build()),
                Arguments.of("Missing issuer", claims(USER.name())
                        .claims(values -> values.remove("iss")).build()),
                Arguments.of("Wrong audience", claims(USER.name()).audience(List.of("other-service")).build()),
                Arguments.of("Missing audience", claims(USER.name())
                        .claims(values -> values.remove("aud")).build())
        );
    }

    private static Stream<MockHttpServletRequestBuilder> protectedRequests() {
        return Stream.concat(
                Stream.of(
                        get("/api/products"),
                        get("/api/products/{productId}", PRODUCT_ID),
                        get("/api/reservations/{reservationId}", RESERVATION_ID),
                        post("/api/reservations")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
                                .contentType(APPLICATION_JSON)
                                .content(createReservationJson()),
                        post("/api/reservations/{reservationId}/confirm", RESERVATION_ID),
                        post("/api/reservations/{reservationId}/cancel", RESERVATION_ID)
                ),
                productWriteRequests()
        );
    }

    private static Stream<MockHttpServletRequestBuilder> productWriteRequests() {
        return Stream.of(
                post("/api/products").contentType(APPLICATION_JSON).content(createProductJson()),
                put("/api/products/{productId}", PRODUCT_ID)
                        .contentType(APPLICATION_JSON).content(updateProductJson()),
                post("/api/products/{productId}/inventory/restock", PRODUCT_ID)
                        .contentType(APPLICATION_JSON).content(restockJson())
        );
    }

    private static Product product() {
        return new Product(
                PRODUCT_ID, "PHONE-001", "Smartphone", "Test product",
                new BigDecimal("799.99"), "USD", true, 0, null, NOW, NOW
        );
    }

    private static Reservation reservation() {
        return new Reservation(
                RESERVATION_ID, PRODUCT_ID, 3, ReservationStatus.ACTIVE,
                NOW.plusSeconds(900), 0, NOW, NOW
        );
    }

    private static String createProductJson() {
        return """
                {
                  "sku": "PHONE-001",
                  "name": "Smartphone",
                  "description": "Test product",
                  "price": 799.99,
                  "currencyCode": "USD",
                  "initialQuantity": 10
                }
                """;
    }

    private static String updateProductJson() {
        return """
                {
                  "name": "Smartphone Pro",
                  "description": "Updated product",
                  "price": 899.99,
                  "currencyCode": "USD",
                  "active": true,
                  "version": 0
                }
                """;
    }

    private static String restockJson() {
        return """
                {
                  "quantity": 5,
                  "version": 0
                }
                """;
    }

    private static String createReservationJson() {
        return """
                {
                  "productId": "d479265e-8dec-4cfc-b5e8-ab0141e44b3a",
                  "quantity": 3
                }
                """;
    }
}
