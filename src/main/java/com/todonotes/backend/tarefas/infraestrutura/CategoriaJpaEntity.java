package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Categoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Espelho da tabela {@code categorias}.
 *
 * <p>Sem {@code unique = true} no {@code nome}: a unicidade é por dono e sem diferenciar
 * maiúscula de minúscula, e quem a garante é o índice funcional
 * {@code uq_categorias_nome_por_usuario}, que o JPA não sabe declarar. Anotar
 * {@code unique} aqui descreveria uma regra que não existe — e com
 * {@code ddl-auto: validate} nem seria conferido, porque a validação olha colunas e tipos,
 * não restrições.
 */
@Entity
@Table(name = "categorias")
public class CategoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nome;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected CategoriaJpaEntity() {
    }

    public CategoriaJpaEntity(Long id, String nome, Long usuarioId, Instant criadoEm) {
        this.id = id;
        this.nome = nome;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }

    public static CategoriaJpaEntity deDominio(Categoria categoria) {
        return new CategoriaJpaEntity(
            categoria.getId(), categoria.getNome(), categoria.getUsuarioId(), categoria.getCriadoEm()
        );
    }

    public Categoria paraDominio() {
        return new Categoria(id, nome, usuarioId, criadoEm);
    }
}
