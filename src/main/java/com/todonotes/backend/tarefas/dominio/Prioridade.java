package com.todonotes.backend.tarefas.dominio;

/**
 * A faixa em que a tarefa vive.
 *
 * <p><b>A ordem de declaração é a ordem da lista</b>, e não um detalhe de escrita: a
 * ordenação em memória usa o ordinal, e a do banco usa o tipo {@code ENUM} nativo
 * {@code prioridade_tarefa}, que ordena pela ordem em que os valores foram declarados na
 * {@code V1}. <b>As duas declarações precisam dizer o mesmo</b> — invertê-las em um só lado
 * faria a lista sair numa ordem no banco e noutra na tela, sem erro em lugar nenhum.
 */
public enum Prioridade {
    ALTA,
    MEDIA,
    BAIXA
}
