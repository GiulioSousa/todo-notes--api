package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.GrupoDeCategoria;

import java.util.List;

/**
 * Um grupo de categoria como a <b>tela</b> o vê: filtrado pelo que o dono pediu, com a
 * categoria já resolvida em nome.
 *
 * <p>É o par do {@link FaixaExibida} no outro modo de visualização, e a distinção com o
 * {@link GrupoDeCategoria} do domínio é a mesma: este responde "o que mostrar agora", aquele
 * "onde cada card fica".
 *
 * <p>{@code categoria} <b>nula</b> é o grupo das tarefas sem etiqueta — e o grupo existe de
 * qualquer jeito, inclusive vazio, pelo mesmo motivo de as três faixas virem sempre: grupo
 * vazio continua sendo área de soltura do arrastar, e soltar um card nele é o que tira a
 * etiqueta dele.
 */
public record GrupoExibido(Categoria categoria, List<TarefaComCategoria> tarefas) {
}
