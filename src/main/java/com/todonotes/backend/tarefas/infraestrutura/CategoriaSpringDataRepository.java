package com.todonotes.backend.tarefas.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriaSpringDataRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    Optional<CategoriaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<CategoriaJpaEntity> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    /**
     * A única consulta escrita à mão deste contexto, e por um motivo preciso: o índice
     * único é {@code (usuario_id, lower(nome))}, e o {@code IgnoreCase} do Spring Data
     * gera {@code upper(nome) = upper(?)}. As duas comparações dão o mesmo resultado, mas
     * só esta usa o índice — a outra faria varredura da tabela e, pior, divergiria da
     * regra que o banco aplica.
     */
    @Query("""
        SELECT c FROM CategoriaJpaEntity c
         WHERE c.usuarioId = :usuarioId
           AND lower(c.nome) = lower(:nome)
        """)
    Optional<CategoriaJpaEntity> buscarPorNomeIgnorandoCaixa(
        @Param("usuarioId") Long usuarioId, @Param("nome") String nome
    );
}
