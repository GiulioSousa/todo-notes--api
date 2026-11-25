package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.aplicacao.GrupoExibido;

import java.util.List;

/**
 * Um grupo na resposta agrupada por categoria: a categoria e os cards dela, em ordem.
 *
 * <p>{@code categoria} <b>nula</b> é o grupo das tarefas sem etiqueta. Vem como objeto nulo,
 * e não como um id especial nem um nome inventado: a tela escreve o rótulo que quiser, e a
 * API não finge que existe uma categoria chamada "Sem categoria".
 *
 * <p>Grupo vazio <b>não</b> é omitido, pelo mesmo motivo das três faixas: grupo vazio
 * continua sendo área de soltura do arrastar, e é por ele que se põe o primeiro card numa
 * categoria recém-criada.
 */
public record RespostaGrupoDeCategoria(RespostaCategoria categoria, List<RespostaTarefa> tarefas) {

    public static RespostaGrupoDeCategoria aPartirDoDominio(GrupoExibido grupo) {
        return new RespostaGrupoDeCategoria(
            grupo.categoria() == null ? null : RespostaCategoria.aPartirDoDominio(grupo.categoria()),
            grupo.tarefas().stream().map(RespostaTarefa::aPartirDoDominio).toList()
        );
    }
}
