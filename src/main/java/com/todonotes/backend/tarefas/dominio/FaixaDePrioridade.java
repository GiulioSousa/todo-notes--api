package com.todonotes.backend.tarefas.dominio;

import java.util.List;
import java.util.Objects;

/**
 * As tarefas de uma prioridade, em ordem: o grupo do modo de visualização por prioridade.
 *
 * <p>Toda a regra de ordenação está na {@link GrupoDeTarefas}, que esta classe compartilha
 * com o {@link GrupoDeCategoria}. O que é só desta: a chave é a {@link Prioridade}, a posição
 * lida e escrita é a {@code posicaoNaPrioridade}, e inserir aqui é o que muda a prioridade de
 * um card.
 */
public final class FaixaDePrioridade extends GrupoDeTarefas<FaixaDePrioridade> {

    private final Prioridade prioridade;

    private FaixaDePrioridade(Prioridade prioridade, List<Tarefa> tarefas) {
        super(tarefas);
        this.prioridade = prioridade;
    }

    /** Monta a faixa a partir do que veio do banco, em ordem e numerada de 0. */
    public static FaixaDePrioridade de(Prioridade prioridade, List<Tarefa> tarefas) {
        Objects.requireNonNull(prioridade, "A faixa precisa de uma prioridade");
        return new FaixaDePrioridade(prioridade, tarefas).normalizado();
    }

    public static FaixaDePrioridade vazia(Prioridade prioridade) {
        return de(prioridade, List.of());
    }

    public Prioridade prioridade() {
        return prioridade;
    }

    @Override
    protected int posicaoNoGrupo(Tarefa tarefa) {
        return tarefa.getPosicaoNaPrioridade();
    }

    @Override
    protected Tarefa comLugarNesteGrupo(Tarefa tarefa, int posicao) {
        return tarefa.comLugar(prioridade, posicao);
    }

    @Override
    protected FaixaDePrioridade comTarefas(List<Tarefa> tarefas) {
        return new FaixaDePrioridade(prioridade, tarefas);
    }

    @Override
    protected Object chave() {
        return prioridade;
    }
}
