package com.todonotes.backend.tarefas.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Etiqueta de uma tarefa, e nada além disso.
 *
 * <p>Não tem cor, nem ícone, nem ordem própria, e excluí-la não apaga tarefa nenhuma: o
 * {@code ON DELETE SET NULL (categoria_id)} apenas as desetiqueta. É o que permite apagar
 * uma categoria sem pensar duas vezes — e é a razão de o {@code DELETE} responder
 * {@code 204} sem dizer quantas tarefas foram afetadas.
 *
 * <p><b>A unicidade do nome não é conferida aqui.</b> Ela é por dono e sem diferenciar
 * maiúscula de minúscula, o que exige olhar as outras linhas — coisa que uma entidade não
 * faz. Quem confere é o {@code CategoriaService}, pela porta; o índice
 * {@code uq_categorias_nome_por_usuario} é a última palavra, no banco.
 */
public class Categoria {

    private final Long id;
    private final String nome;
    private final Long usuarioId;
    private final Instant criadoEm;

    public Categoria(Long id, String nome, Long usuarioId, Instant criadoEm) {
        this.id = id;
        this.nome = nome;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }

    public static Categoria nova(String nome, Long usuarioId, Instant agora) {
        return new Categoria(
            null, exigirNome(nome),
            Objects.requireNonNull(usuarioId, "A categoria precisa de um dono"),
            agora
        );
    }

    public Categoria comNome(String novoNome) {
        return new Categoria(id, exigirNome(novoNome), usuarioId, criadoEm);
    }

    /**
     * Apara as pontas e recusa o vazio — a mesma regra do {@code ck_categorias_nome}.
     *
     * <p>A grafia digitada é a que fica: {@code Casa} e {@code casa} são a mesma categoria
     * para efeito de unicidade, mas o nome é gravado como veio. Normalizar para minúscula
     * aqui pouparia a comparação e cobraria um nome feio em toda a tela.
     */
    private static String exigirNome(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria é obrigatório");
        }
        return limpo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
