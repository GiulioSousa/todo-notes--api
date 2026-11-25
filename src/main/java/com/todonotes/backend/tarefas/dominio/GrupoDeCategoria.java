package com.todonotes.backend.tarefas.dominio;

import java.util.List;

/**
 * As tarefas de uma categoria, em ordem: o grupo do modo de visualização por categoria.
 *
 * <p>Toda a regra de ordenação está na {@link GrupoDeTarefas}, que esta classe compartilha
 * com a {@link FaixaDePrioridade}. O que é só desta: a chave é o {@code categoriaId}, a
 * posição lida e escrita é a {@code posicaoNaCategoria}, e inserir aqui é o que muda a
 * categoria de um card.
 *
 * <p><b>{@code categoriaId} nulo é um grupo, e não a ausência de um</b> — o das tarefas sem
 * etiqueta. É o grupo que recebe os órfãos de toda categoria excluída, e o único que não pode
 * deixar de existir: nenhuma tela o esconde, porque esconder cards sem etiqueta seria fazer
 * tarefa desaparecer num modo de visualização inteiro. No banco ele é um grupo como os
 * outros porque a restrição é {@code NULLS NOT DISTINCT}; sem isso, as tarefas sem etiqueta
 * ficariam fora da unicidade e este grupo seria o único sem ordem garantida.
 */
public final class GrupoDeCategoria extends GrupoDeTarefas<GrupoDeCategoria> {

    private final Long categoriaId;

    private GrupoDeCategoria(Long categoriaId, List<Tarefa> tarefas) {
        super(tarefas);
        this.categoriaId = categoriaId;
    }

    /**
     * Monta o grupo a partir do que veio do banco, em ordem e numerado de 0.
     *
     * <p>{@code categoriaId} nulo monta o grupo das sem etiqueta, e não há sobrecarga para
     * isso: a nulidade é a chave, não um parâmetro faltando.
     */
    public static GrupoDeCategoria de(Long categoriaId, List<Tarefa> tarefas) {
        return new GrupoDeCategoria(categoriaId, tarefas).normalizado();
    }

    public static GrupoDeCategoria vazio(Long categoriaId) {
        return de(categoriaId, List.of());
    }

    /** O id da categoria, ou {@code null} no grupo das tarefas sem etiqueta. */
    public Long categoriaId() {
        return categoriaId;
    }

    @Override
    protected int posicaoNoGrupo(Tarefa tarefa) {
        return tarefa.getPosicaoNaCategoria();
    }

    @Override
    protected Tarefa comLugarNesteGrupo(Tarefa tarefa, int posicao) {
        return tarefa.comLugarNaCategoria(categoriaId, posicao);
    }

    @Override
    protected GrupoDeCategoria comTarefas(List<Tarefa> tarefas) {
        return new GrupoDeCategoria(categoriaId, tarefas);
    }

    @Override
    protected Object chave() {
        return categoriaId == null ? "sem categoria" : categoriaId;
    }
}
