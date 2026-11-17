package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.Tarefa;

/**
 * Uma tarefa com a sua categoria já resolvida.
 *
 * <p>A {@link Tarefa} guarda só o {@code categoriaId} — é o que mantém o domínio sem
 * carregar texto que ele nunca lê. Mas a etiqueta aparece em todo card, e resolvê-la no
 * cliente obrigaria a lista de categorias a estar carregada antes da primeira pintura. O
 * encontro dos dois acontece aqui, na camada de aplicação, que é onde se pode consultar
 * dois repositórios.
 *
 * <p>{@code categoria} é <b>nula</b> quando a tarefa não tem etiqueta — estado normal, e o
 * que sobra quando a categoria é excluída.
 */
public record TarefaComCategoria(Tarefa tarefa, Categoria categoria) {
}
