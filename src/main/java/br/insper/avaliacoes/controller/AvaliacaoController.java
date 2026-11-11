package br.insper.avaliacoes.controller;

import br.insper.avaliacoes.dto.CriarAvaliacaoRequest;
import br.insper.avaliacoes.model.Avaliacao;
import br.insper.avaliacoes.service.AvaliacaoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/avaliacoes")
public class AvaliacaoController {
    private final AvaliacaoService service;
    private final String namespace;
    public AvaliacaoController(AvaliacaoService service, @Value("${auth0.claim-namespace}") String namespace) {
        this.service = service;
        this.namespace = namespace;
    }
    @GetMapping
    public List<Avaliacao> listar() { return service.listar(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Avaliacao criar(@Valid @RequestBody CriarAvaliacaoRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(request, jwt.getClaimAsString(namespace + "/email"));
    }
    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }
}
