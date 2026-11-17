package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Nenhuma consulta escrita à mão, e o filtro opcional de categoria resolvido por <b>dois
 * métodos</b> em vez de um {@code :categoriaId is null or ...}.
 *
 * <p>É a convenção do GeFinX, e o motivo está anotado lá: a comparação com nulo dentro do
 * {@code WHERE} tira a coluna do índice. Com um só filtro opcional, duas derivações custam
 * um {@code if} no adaptador; o {@code JpaSpecificationExecutor} que ele usa na listagem
 * paginada é para quando os filtros opcionais são vários.
 *
 * <p>O {@code OrderByPrioridadeAsc} ordena pela coluna no banco, que é do tipo {@code ENUM}
 * nativo — ou seja, pela ordem de declaração dos valores, não alfabética. É o que faz
 * {@code ALTA} vir antes de {@code BAIXA} sem nenhuma tradução no caminho.
 */
public interface TarefaSpringDataRepository extends JpaRepository<TarefaJpaEntity, Long> {

    Optional<TarefaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<TarefaJpaEntity> findByUsuarioIdOrderByPrioridadeAscPosicaoAsc(Long usuarioId);

    List<TarefaJpaEntity> findByUsuarioIdAndCategoriaIdOrderByPrioridadeAscPosicaoAsc(
        Long usuarioId, Long categoriaId
    );

    List<TarefaJpaEntity> findByUsuarioIdAndPrioridadeOrderByPosicaoAsc(
        Long usuarioId, Prioridade prioridade
    );

    int countByUsuarioIdAndPrioridade(Long usuarioId, Prioridade prioridade);
}
