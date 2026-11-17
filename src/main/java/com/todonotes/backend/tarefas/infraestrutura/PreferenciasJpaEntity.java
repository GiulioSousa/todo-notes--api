package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Preferencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Espelho da tabela {@code preferencias}. Uma linha por dono, garantida por
 * {@code uq_preferencias_usuario}.
 */
@Entity
@Table(name = "preferencias")
public class PreferenciasJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "ocultar_concluidas_automaticamente", nullable = false)
    private boolean ocultarConcluidasAutomaticamente;

    /**
     * A coluna é {@code SMALLINT} e o campo é {@code int}, e os dois tipos não são o mesmo
     * para o Hibernate: sem o {@link SqlTypes#SMALLINT} explícito, ele esperaria
     * {@code INTEGER} e o {@code ddl-auto: validate} derrubaria a aplicação na subida.
     *
     * <p>A alternativa seria um {@code short} aqui e no domínio — um tipo estreito vazando
     * para as regras por causa do tamanho da coluna, que guarda no máximo 365.
     */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "dias_para_ocultar", nullable = false)
    private int diasParaOcultar;

    protected PreferenciasJpaEntity() {
    }

    public PreferenciasJpaEntity(
        Long id, Long usuarioId, boolean ocultarConcluidasAutomaticamente, int diasParaOcultar
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.ocultarConcluidasAutomaticamente = ocultarConcluidasAutomaticamente;
        this.diasParaOcultar = diasParaOcultar;
    }

    public static PreferenciasJpaEntity deDominio(Preferencias preferencias) {
        return new PreferenciasJpaEntity(
            preferencias.getId(), preferencias.getUsuarioId(),
            preferencias.isOcultarConcluidasAutomaticamente(), preferencias.getDiasParaOcultar()
        );
    }

    public Preferencias paraDominio() {
        return new Preferencias(id, usuarioId, ocultarConcluidasAutomaticamente, diasParaOcultar);
    }
}
