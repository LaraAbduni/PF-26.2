package br.insper.avaliacoes.repository;

import br.insper.avaliacoes.model.Avaliacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    List<Avaliacao> findAllByOrderByDataAvaliacaoDescIdDesc();
}
