package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.aplicacao.FaixaExibida;

import java.util.List;

/**
 * O envelope da listagem: {@code { "faixas": [...] }}.
 *
 * <p>Um objeto em volta do vetor, e não o vetor solto, porque a resposta deste recurso tende
 * a crescer — uma contagem de concluídas ocultas, por exemplo, é o tipo de coisa que a tela
 * vai querer. Com o vetor na raiz, acrescentar qualquer campo quebraria o cliente; com o
 * envelope, não.
 *
 * <p>É também o formato do {@code PATCH /lugar}, que devolve apenas as faixas afetadas — uma
 * ou duas — para o cliente não precisar recarregar a lista inteira.
 */
public record RespostaListaDeTarefas(List<RespostaFaixa> faixas) {

    public static RespostaListaDeTarefas aPartirDoDominio(List<FaixaExibida> faixas) {
        return new RespostaListaDeTarefas(
            faixas.stream().map(RespostaFaixa::aPartirDoDominio).toList()
        );
    }
}
