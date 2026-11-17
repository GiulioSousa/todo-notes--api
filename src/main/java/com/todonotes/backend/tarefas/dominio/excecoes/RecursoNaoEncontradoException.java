package com.todonotes.backend.tarefas.dominio.excecoes;

/**
 * Vira {@code 404} no {@link
 * com.todonotes.backend.compartilhado.excecoes.ManipuladorGlobalDeExcecoes}.
 *
 * <p><b>É também a resposta para recurso de outro dono</b>, e isso é deliberado: um
 * {@code 403} confirmaria que aquele id existe, e quem quisesse descobrir quantas tarefas
 * há no sistema só precisaria contar os {@code 403} entre os {@code 404}.
 *
 * <p>A mensagem não diz o id de volta. O cliente já o tem, e repeti-lo só serviria para
 * alimentar o log com valores vindos de fora.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException tarefa() {
        return new RecursoNaoEncontradoException("Tarefa não encontrada");
    }

    public static RecursoNaoEncontradoException categoria() {
        return new RecursoNaoEncontradoException("Categoria não encontrada");
    }
}
