package com.todonotes.backend.tarefas.dominio;

/**
 * O ciclo de vida da tarefa.
 *
 * <p>Diferente de {@link Prioridade}, aqui a ordem de declaração não significa nada: a
 * coluna é {@code VARCHAR} com {@code CHECK}, e nada ordena por status.
 */
public enum Status {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDA;

    public boolean concluida() {
        return this == CONCLUIDA;
    }
}
