package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.dominio.Categoria;

/**
 * A categoria como a API a devolve — na lista de categorias e embutida em cada card.
 *
 * <p>Sem {@code criadoEm}: a data existe na tabela para dar ordem a um empate de nome, e
 * nenhuma tela a mostra. Devolver campo que ninguém lê é contrato a mais para manter.
 */
public record RespostaCategoria(Long id, String nome) {

    public static RespostaCategoria aPartirDoDominio(Categoria categoria) {
        return new RespostaCategoria(categoria.getId(), categoria.getNome());
    }
}
