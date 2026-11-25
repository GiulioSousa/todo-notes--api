package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.aplicacao.GrupoExibido;

import java.util.List;

/**
 * O envelope da listagem agrupada por categoria: {@code { "grupos": [...] }}.
 *
 * <p>Envelope pelo mesmo motivo do {@link RespostaListaDeTarefas}: com o vetor na raiz,
 * acrescentar qualquer campo depois quebraria o cliente.
 *
 * <p><b>É um envelope diferente, e não o mesmo com outra chave dentro</b>, porque a chave do
 * grupo é de outra natureza: na faixa é um enum que o cliente conhece de antemão, aqui é uma
 * categoria que ele acabou de cadastrar — ou nenhuma. Uma rota só, respondendo os dois
 * formatos conforme um parâmetro, obrigaria o cliente a dois caminhos de leitura de qualquer
 * jeito, e mais um para descobrir qual deles usar.
 *
 * <p>É também o formato do {@code PATCH /lugar-na-categoria}, que devolve apenas os grupos
 * afetados — um ou dois.
 */
public record RespostaListaPorCategoria(List<RespostaGrupoDeCategoria> grupos) {

    public static RespostaListaPorCategoria aPartirDoDominio(List<GrupoExibido> grupos) {
        return new RespostaListaPorCategoria(
            grupos.stream().map(RespostaGrupoDeCategoria::aPartirDoDominio).toList()
        );
    }
}
