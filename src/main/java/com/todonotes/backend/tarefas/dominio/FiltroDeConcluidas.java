package com.todonotes.backend.tarefas.dominio;

/**
 * O que fazer com as tarefas concluídas ao listar. É o parâmetro {@code concluidas} do
 * {@code GET /api/tarefas}.
 *
 * <p>Três valores, e não um {@code boolean}, porque há três respostas diferentes — e a do
 * meio, {@link #PADRAO}, é a única que consulta as {@link Preferencias}.
 */
public enum FiltroDeConcluidas {

    /** Aplica a preferência do dono: a regra de ocultar depois de X dias, se estiver ligada. */
    PADRAO,

    /** Ignora a regra e mostra tudo — é como se revê o que o tempo escondeu. */
    TODAS,

    /** Esconde toda tarefa concluída, independente de data. É o botão de ocultar. */
    NENHUMA
}
