package com.todonotes.backend.tarefas.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência das categorias.
 *
 * <p>Como em {@link RepositorioTarefa}, o dono viaja em toda leitura: categoria de outro
 * dono não é {@code 403}, é {@code 404}.
 */
public interface RepositorioCategoria {

    Categoria salvar(Categoria categoria);

    Optional<Categoria> buscarPorIdEUsuario(Long id, Long usuarioId);

    /** Ordenada por nome — é como a tela a mostra, e como o seletor a oferece. */
    List<Categoria> listarPorUsuario(Long usuarioId);

    /**
     * Procura pelo nome <b>sem diferenciar maiúscula de minúscula</b>, a mesma regra do
     * índice {@code uq_categorias_nome_por_usuario}.
     *
     * <p>Devolve a categoria, e não um {@code boolean}, porque serve aos dois casos: ao
     * criar, qualquer resultado é conflito; ao renomear, só é conflito se o id for de
     * outra. Um {@code existePorNome(...)} não distinguiria "o nome está em uso" de "o nome
     * está em uso por ela mesma", e corrigir {@code casa} para {@code Casa} daria
     * {@code 409}.
     */
    Optional<Categoria> buscarPorNomeEUsuario(String nome, Long usuarioId);

    /**
     * Exclui. As tarefas que a usavam perdem a etiqueta pelo
     * {@code ON DELETE SET NULL (categoria_id)} — nada a fazer aqui.
     */
    void excluir(Long id);
}
