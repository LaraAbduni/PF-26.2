package br.insper.avaliacoes;

import br.insper.avaliacoes.repository.AvaliacaoRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AvaliacaoIntegrationTest {
    private static final String NAMESPACE = "https://pf-avaliacoes.example.com";
    private static final String BODY = """
        {"emailAvaliado":"colega@empresa.com","comentarios":"Ótima colaboração no projeto.","nota":9.5}
        """;
    @Autowired MockMvc mvc;
    @Autowired AvaliacaoRepository repository;
    @MockBean JwtDecoder decoder;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        when(decoder.decode("admin")).thenReturn(token("admin", List.of("ADMIN"), "admin@empresa.com"));
        when(decoder.decode("user")).thenReturn(token("user", List.of("USER"), "user@empresa.com"));
        when(decoder.decode("sem-role")).thenReturn(token("sem-role", List.of(), "sem@empresa.com"));
        when(decoder.decode("sem-email")).thenReturn(token("sem-email", List.of("ADMIN"), null));
        when(decoder.decode("invalido")).thenThrow(new BadJwtException("Token inválido"));
    }
    private Jwt token(String value, List<String> roles, String email) {
        var builder = Jwt.withTokenValue(value).header("alg", "RS256").subject("auth0|" + value)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).claim(NAMESPACE + "/roles", roles);
        if (email != null) builder.claim(NAMESPACE + "/email", email);
        return builder.build();
    }
    @Test void todasAsRotasDeNegocioExigemLogin() throws Exception {
        mvc.perform(get("/avaliacoes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/avaliacoes").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }
    @Test void tokenInvalidoRecebe401() throws Exception {
        mvc.perform(get("/avaliacoes").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }
    @Test void adminCriaEUserListaDadosPersistidos() throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.emailAvaliador").value("admin@empresa.com"))
            .andExpect(jsonPath("$.nota").value(9.5)).andExpect(jsonPath("$.dataAvaliacao").exists());
        mvc.perform(get("/avaliacoes").header("Authorization", "Bearer user"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].emailAvaliado").value("colega@empresa.com"));
        assertThat(repository.count()).isEqualTo(1);
    }
    @Test void userNaoConsegueCadastrarNemChamandoDiretamenteAApi() throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer user").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }
    @Test void usuarioSemRoleNaoLista() throws Exception {
        mvc.perform(get("/avaliacoes").header("Authorization", "Bearer sem-role")).andExpect(status().isForbidden());
    }
    @Test void identidadeEDataDoAvaliadorNaoPodemSerForjadasNoBody() throws Exception {
        String body = BODY.replace("\"nota\":9.5", "\"nota\":9.5,\"emailAvaliador\":\"outra@empresa.com\",\"dataAvaliacao\":\"2000-01-01T00:00:00Z\"");
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.emailAvaliador").value("admin@empresa.com"));
        assertThat(repository.findAll().get(0).getDataAvaliacao()).isAfter(Instant.now().minusSeconds(30));
    }
    @Test void semEmailAssinadoNaoCadastra() throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer sem-email").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "10.1", "8.99", "null"})
    void validaNota(String nota) throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("9.5", nota))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nota").exists());
        assertThat(repository.count()).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "10"})
    void aceitaExtremosDaNota(String nota) throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("9.5", nota))).andExpect(status().isCreated());
    }
    @Test void validaEmailComentarioEJson() throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"emailAvaliado\":\"invalido\",\"comentarios\":\"   \",\"nota\":8}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.emailAvaliado").exists())
            .andExpect(jsonPath("$.campos.comentarios").exists());
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content("{")).andExpect(status().isBadRequest());
    }
    @Test void listaMaisRecentesPrimeiro() throws Exception {
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON).content(BODY));
        mvc.perform(post("/avaliacoes").header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("colega@empresa.com", "recente@empresa.com")));
        mvc.perform(get("/avaliacoes").header("Authorization", "Bearer admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].emailAvaliado").value("recente@empresa.com"));
    }
    @Test void preflightPermiteSomenteOrigemConfigurada() throws Exception {
        mvc.perform(options("/avaliacoes").header("Origin", "https://pf.example.com")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization,Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://pf.example.com"));
        mvc.perform(options("/avaliacoes").header("Origin", "https://estranho.example.com")
            .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
}
