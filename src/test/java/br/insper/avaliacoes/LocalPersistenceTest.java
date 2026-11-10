package br.insper.avaliacoes;

import br.insper.avaliacoes.model.Avaliacao;
import br.insper.avaliacoes.repository.AvaliacaoRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

class LocalPersistenceTest {
    @Test
    void perfilLocalPreservaAvaliacoesAposReiniciarEExigeAuth0() throws Exception {
        Path database = Files.createTempDirectory(Path.of("target"), "local-db-").resolve("avaliacoes").toAbsolutePath();
        String databaseUrl = "jdbc:h2:file:" + database.toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        Long id;
        try (var first = start(databaseUrl)) {
            assertProtected(first);
            var repository = first.getBean(AvaliacaoRepository.class);
            id = repository.saveAndFlush(new Avaliacao("admin@empresa.com", "colega@empresa.com", "Avaliação persistente.", new BigDecimal("8.5"))).getId();
        }
        assertThat(Files.exists(Path.of(database + ".mv.db"))).isTrue();
        try (var second = start(databaseUrl)) {
            assertProtected(second);
            var saved = second.getBean(AvaliacaoRepository.class).findById(id).orElseThrow();
            assertThat(saved.getComentarios()).isEqualTo("Avaliação persistente.");
            assertThat(saved.getEmailAvaliado()).isEqualTo("colega@empresa.com");
            assertThat(saved.getNota()).isEqualByComparingTo("8.5");
            assertThat(saved.getDataAvaliacao()).isNotNull();
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(AvaliacoesApplication.class).profiles("local").run(
            "--server.port=0", "--spring.datasource.url=" + databaseUrl,
            "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }

    private void assertProtected(ConfigurableApplicationContext context) {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        var response = new TestRestTemplate().getForEntity("http://127.0.0.1:" + port + "/avaliacoes", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
