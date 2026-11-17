package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import com.todonotes.backend.tarefas.dominio.Status;
import com.todonotes.backend.tarefas.dominio.Tarefa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Espelho da tabela {@code tarefas}.
 *
 * <p>A categoria é um {@code Long}, e não um {@code @ManyToOne}: o domínio guarda o id, e
 * uma associação aqui traria o objeto inteiro em toda listagem — mais o risco de carregamento
 * tardio fora da transação. Quem resolve o nome é a camada de aplicação, com uma consulta só
 * para a lista inteira.
 */
@Entity
@Table(name = "tarefas")
public class TarefaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    /** Coluna {@code VARCHAR(14)} com {@code CHECK}, e não tipo próprio: nada ordena por status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Status status;

    /**
     * O ponto mais delicado do mapeamento inteiro.
     *
     * <p>A coluna é do tipo {@code ENUM} nativo {@code prioridade_tarefa}, e não
     * {@code varchar} — é o que faz o banco ordenar pela ordem de declaração dos valores, e
     * o índice {@code uq_tarefas_ordem} servir o {@code ORDER BY} direto. Para o Hibernate
     * enxergar isso são necessárias as duas anotações: {@link SqlTypes#NAMED_ENUM} diz como
     * ler e escrever, e o {@code columnDefinition} dá o nome do tipo, que é o que o
     * {@code ddl-auto: validate} compara contra o banco na subida.
     *
     * <p><b>É aqui que a aplicação falha para subir, se falhar.</b> A combinação está
     * registrada em "Lacunas e riscos conhecidos" do {@code execucao.md}, com a saída —
     * {@code ddl-auto: none} — e o preço dela.
     */
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "prioridade_tarefa")
    private Prioridade prioridade;

    @Column(nullable = false)
    private int posicao;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "data_conclusao")
    private Instant dataConclusao;

    protected TarefaJpaEntity() {
    }

    public TarefaJpaEntity(
        Long id, String titulo, String descricao, Status status, Prioridade prioridade,
        int posicao, Long categoriaId, Long usuarioId, Instant criadaEm, Instant dataConclusao
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.status = status;
        this.prioridade = prioridade;
        this.posicao = posicao;
        this.categoriaId = categoriaId;
        this.usuarioId = usuarioId;
        this.criadaEm = criadaEm;
        this.dataConclusao = dataConclusao;
    }

    /**
     * Id nulo é tarefa nova, e o {@code save} vira {@code INSERT}; com id, vira
     * {@code merge}. A {@code criada_em} vai junto em vez de ficar com o
     * {@code DEFAULT now()} da tabela: é o relógio injetado que a define, para que o teste
     * possa escolher o instante.
     */
    public static TarefaJpaEntity deDominio(Tarefa tarefa) {
        return new TarefaJpaEntity(
            tarefa.getId(), tarefa.getTitulo(), tarefa.getDescricao(), tarefa.getStatus(),
            tarefa.getPrioridade(), tarefa.getPosicao(), tarefa.getCategoriaId(),
            tarefa.getUsuarioId(), tarefa.getCriadaEm(), tarefa.getDataConclusao()
        );
    }

    public Tarefa paraDominio() {
        return new Tarefa(
            id, titulo, descricao, status, prioridade, posicao,
            categoriaId, usuarioId, criadaEm, dataConclusao
        );
    }
}
