package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.aplicacao.FaixaExibida;
import com.todonotes.backend.tarefas.dominio.Prioridade;

import java.util.List;

/**
 * Uma faixa na resposta: a prioridade e os cards dela, em ordem.
 *
 * <p>Faixa vazia <b>não</b> é omitida, e isso é decisão: faixa vazia continua sendo área de
 * soltura do arrastar. Se a API a omitisse, o frontend teria de inventar a lista de
 * prioridades para desenhar o alvo — e passaria a existir, em dois lugares, a verdade sobre
 * quantas faixas há.
 */
public record RespostaFaixa(Prioridade prioridade, List<RespostaTarefa> tarefas) {

    public static RespostaFaixa aPartirDoDominio(FaixaExibida faixa) {
        return new RespostaFaixa(
            faixa.prioridade(),
            faixa.tarefas().stream().map(RespostaTarefa::aPartirDoDominio).toList()
        );
    }
}
