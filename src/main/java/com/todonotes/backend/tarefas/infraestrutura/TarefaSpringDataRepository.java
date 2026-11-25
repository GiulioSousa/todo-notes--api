package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Nenhuma consulta escrita à mão, e os filtros anuláveis resolvidos por <b>dois métodos
 * cada</b> em vez de um {@code :valor is null or ...}.
 *
 * <p>É a convenção do GeFinX, e o motivo está anotado lá: a comparação com nulo dentro do
 * {@code WHERE} tira a coluna do índice. Com um só filtro opcional, duas derivações custam
 * um {@code if} no adaptador; o {@code JpaSpecificationExecutor} que ele usa na listagem
 * paginada é para quando os filtros opcionais são vários.
 *
 * <p><b>No grupo de categoria, os dois métodos não dizem a mesma coisa de formas
 * diferentes.</b> {@code AndCategoriaId} é um grupo; {@code AndCategoriaIdIsNull} é outro
 * grupo, o das tarefas sem etiqueta. A diferença entre chave nula e filtro ausente é o que
 * separa "o grupo das sem categoria" de "todas as categorias".
 *
 * <p>O {@code OrderByPrioridadeAsc} ordena pela coluna no banco, que é do tipo {@code ENUM}
 * nativo — ou seja, pela ordem de declaração dos valores, não alfabética. É o que faz
 * {@code ALTA} vir antes de {@code BAIXA} sem nenhuma tradução no caminho.
 */
public interface TarefaSpringDataRepository extends JpaRepository<TarefaJpaEntity, Long> {

    Optional<TarefaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<TarefaJpaEntity> findByUsuarioIdOrderByPrioridadeAscPosicaoNaPrioridadeAsc(Long usuarioId);

    List<TarefaJpaEntity> findByUsuarioIdAndCategoriaIdOrderByPrioridadeAscPosicaoNaPrioridadeAsc(
        Long usuarioId, Long categoriaId
    );

    List<TarefaJpaEntity> findByUsuarioIdAndPrioridadeOrderByPosicaoNaPrioridadeAsc(
        Long usuarioId, Prioridade prioridade
    );

    int countByUsuarioIdAndPrioridade(Long usuarioId, Prioridade prioridade);

    /**
     * A lista na ordem do outro agrupamento.
     *
     * <p>As tarefas sem etiqueta saem no fim: o PostgreSQL ordena nulos por último no
     * {@code ASC}, e é onde o grupo delas aparece na tela de qualquer maneira.
     */
    List<TarefaJpaEntity> findByUsuarioIdOrderByCategoriaIdAscPosicaoNaCategoriaAsc(Long usuarioId);

    List<TarefaJpaEntity> findByUsuarioIdAndCategoriaIdOrderByPosicaoNaCategoriaAsc(
        Long usuarioId, Long categoriaId
    );

    List<TarefaJpaEntity> findByUsuarioIdAndCategoriaIdIsNullOrderByPosicaoNaCategoriaAsc(
        Long usuarioId
    );

    int countByUsuarioIdAndCategoriaId(Long usuarioId, Long categoriaId);

    int countByUsuarioIdAndCategoriaIdIsNull(Long usuarioId);
}
