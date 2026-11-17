package com.todonotes.backend.usuarios.infraestrutura;

import com.todonotes.backend.usuarios.dominio.Usuario;
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
 * Espelho da tabela {@code usuarios}.
 *
 * <p>Existe separada de {@link Usuario} porque o domínio não pode importar JPA. O preço é
 * uma classe a mais e um {@link #paraDominio()}; o que se compra é um domínio que não muda
 * quando o mapeamento mudar.
 *
 * <p>Só de leitura, como a entidade de domínio: a conta nasce do
 * {@code criar-usuario.sql}. Não há {@code deDominio()}.
 */
@Entity
@Table(name = "usuarios")
public class UsuarioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String usuario;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    /**
     * A coluna é {@code TIMESTAMPTZ}, e {@link SqlTypes#TIMESTAMP_UTC} é o código que o
     * Hibernate traduz para ela. É o padrão dele para {@link Instant} desde a versão 6, e
     * está escrito assim mesmo: com {@code ddl-auto: validate}, o mapeamento implícito que
     * mudar numa atualização do framework derruba a aplicação na subida, e a anotação torna
     * a intenção verificável em vez de presumida.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected UsuarioJpaEntity() {
    }

    public UsuarioJpaEntity(Long id, String usuario, String senhaHash, Instant criadoEm) {
        this.id = id;
        this.usuario = usuario;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public Usuario paraDominio() {
        return new Usuario(id, usuario, senhaHash, criadoEm);
    }
}
