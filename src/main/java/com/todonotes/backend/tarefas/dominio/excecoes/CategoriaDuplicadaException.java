package com.todonotes.backend.tarefas.dominio.excecoes;

/**
 * Vira {@code 409} no {@link
 * com.todonotes.backend.compartilhado.excecoes.ManipuladorGlobalDeExcecoes}.
 *
 * <p>O conflito é por dono e <b>sem diferenciar maiúscula de minúscula</b>: com
 * {@code Casa} cadastrada, {@code casa} e {@code CASA} são a mesma categoria. Sem essa
 * regra, a lista acumularia três etiquetas que a pessoa lê como uma só.
 *
 * <p>A mensagem repete o nome porque ele veio de um campo que a pessoa acabou de digitar e
 * está vendo na tela — diferente de um id, dizê-lo de volta ajuda a entender o que houve.
 */
public class CategoriaDuplicadaException extends RuntimeException {

    public CategoriaDuplicadaException(String nome) {
        super("Já existe uma categoria chamada \"" + nome + "\"");
    }
}
