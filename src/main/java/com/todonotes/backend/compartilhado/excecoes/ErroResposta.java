package com.todonotes.backend.compartilhado.excecoes;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de erro da API. O mapa {@code erros} vem vazio fora de falha de validação,
 * e nunca nulo: o cliente trata um formato, não dois.
 */
public record ErroResposta(Instant momento, int status, String mensagem, Map<String, String> erros) {

    public ErroResposta(int status, String mensagem) {
        this(Instant.now(), status, mensagem, Map.of());
    }

    public ErroResposta(int status, String mensagem, Map<String, String> erros) {
        this(Instant.now(), status, mensagem, erros);
    }
}
