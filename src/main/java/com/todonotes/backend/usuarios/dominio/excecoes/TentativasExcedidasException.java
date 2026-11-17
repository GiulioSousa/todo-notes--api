package com.todonotes.backend.usuarios.dominio.excecoes;

/**
 * Vira {@code 429} com {@code Retry-After} no {@link
 * com.todonotes.backend.compartilhado.excecoes.ManipuladorGlobalDeExcecoes}.
 *
 * <p>Os segundos viajam na exceção porque o cliente precisa deles no cabeçalho, e porque a
 * mensagem sozinha não serve para a tela decidir quando reabilitar o botão.
 */
public class TentativasExcedidasException extends RuntimeException {

    private final long segundosParaLiberar;

    public TentativasExcedidasException(long segundosParaLiberar) {
        super("Muitas tentativas. Tente novamente em " + segundosParaLiberar + " segundos.");
        this.segundosParaLiberar = segundosParaLiberar;
    }

    public long getSegundosParaLiberar() {
        return segundosParaLiberar;
    }
}
