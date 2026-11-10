package br.insper.avaliacoes.service;

import br.insper.avaliacoes.dto.CriarAvaliacaoRequest;
import br.insper.avaliacoes.model.Avaliacao;
import br.insper.avaliacoes.repository.AvaliacaoRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvaliacaoService {
    private final AvaliacaoRepository repository;
    public AvaliacaoService(AvaliacaoRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<Avaliacao> listar() { return repository.findAllByOrderByDataAvaliacaoDescIdDesc(); }

    @Transactional
    public Avaliacao criar(CriarAvaliacaoRequest request, String emailAvaliador) {
        if (emailAvaliador == null || emailAvaliador.isBlank() || emailAvaliador.length() > 254) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "E-mail ausente no token. Configure a Action do Auth0 e entre novamente.");
        }
        return repository.save(new Avaliacao(emailAvaliador, request.emailAvaliado().trim(),
            request.comentarios().trim(), request.nota()));
    }
}
