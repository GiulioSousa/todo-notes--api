package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.FaixaDePrioridade;
import com.todonotes.backend.tarefas.dominio.Prioridade;

import java.util.List;

/**
 * Uma faixa como a <b>tela</b> a vê: filtrada pelo que o dono pediu, com as categorias já
 * resolvidas.
 *
 * <p>Não confundir com a {@link FaixaDePrioridade} do domínio, que é a faixa como o
 * <b>banco</b> a guarda: inteira, sem filtro, e com as posições que definem a ordem. São
 * dois objetos porque são dois lados — um responde "o que mostrar agora", o outro "onde cada
 * card fica".
 *
 * <p>A distinção não é acadêmica: <b>com um filtro ligado, o índice do card na tela deixa
 * de ser a posição dele na faixa</b>, e arrastar depende de uma tradução que mora no cliente
 * (o {@code posicao-na-faixa.ts}), porque é ele que sabe quais filtros aplicou.
 *
 * <p>O par desta classe no outro modo de visualização é o {@link GrupoExibido}.
 */
public record FaixaExibida(Prioridade prioridade, List<TarefaComCategoria> tarefas) {
}
