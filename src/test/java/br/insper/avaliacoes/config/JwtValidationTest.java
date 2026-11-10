package br.insper.avaliacoes.config;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtValidationTest {
    static RSAKey key;
    static HttpServer server;
    static String issuer;
    static JwtDecoder decoder;
    @BeforeAll static void setup() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            byte[] bytes = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        decoder = new SecurityConfig().jwtDecoder(issuer, "https://api.pf-avaliacoes");
    }
    @AfterAll static void stop() { if (server != null) server.stop(0); }
    private String signed(String tokenIssuer, String audience, Instant expiration, RSAKey signer) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
            new JWTClaimsSet.Builder().subject("auth0|test").issuer(tokenIssuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(expiration)).build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }
    @Test void aceitaAssinaturaIssuerEAudienceCorretos() throws Exception {
        assertThat(decoder.decode(signed(issuer, "https://api.pf-avaliacoes", Instant.now().plusSeconds(600), key)).getSubject()).isEqualTo("auth0|test");
    }
    @Test void rejeitaIssuerErrado() throws Exception {
        String token = signed("https://outro-tenant.auth0.com/", "https://api.pf-avaliacoes", Instant.now().plusSeconds(600), key);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejeitaTokenParaOutraApi() throws Exception {
        String token = signed(issuer, "https://outra-api", Instant.now().plusSeconds(600), key);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejeitaExpirado() throws Exception {
        String token = signed(issuer, "https://api.pf-avaliacoes", Instant.now().minusSeconds(120), key);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejeitaAssinaturaForjada() throws Exception {
        String token = signed(issuer, "https://api.pf-avaliacoes", Instant.now().plusSeconds(600), new RSAKeyGenerator(2048).generate());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void naoTransformaPermissaoOuRoleDesconhecidaEmAdmin() {
        var converter = new SecurityConfig().jwtAuthenticationConverter("https://pf-avaliacoes.example.com");
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("test")
            .claim("permissions", List.of("ADMIN")).claim("https://pf-avaliacoes.example.com/roles", List.of("SUPERADMIN")).build();
        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }
}
