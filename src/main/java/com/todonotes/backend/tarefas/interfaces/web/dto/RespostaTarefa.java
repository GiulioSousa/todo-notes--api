package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.aplicacao.TarefaComCategoria;
import com.todonotes.backend.tarefas.dominio.Prioridade;
import com.todonotes.backend.tarefas.dominio.Status;
import com.todonotes.backend.tarefas.dominio.Tarefa;

import java.time.Instant;

/**
 * O card, como a API o devolve.
 *
 * <p>A categoria vem <b>embutida, com nome</b>, e não só como {@code categoriaId}: a
 * etiqueta aparece em todo card, e resolvê-la no cliente obrigaria a lista de categorias a
 * estar carregada antes da primeira pintura. Nula quando a tarefa não tem etiqueta.
 *
 * <p>Traz {@code prioridade} e {@code posicao} mesmo dentro de uma faixa que já diz a
 * prioridade: é com esse par que o cliente monta o próximo {@code PATCH /lugar}, e deixá-lo
 * deduzir a posição pelo índice do card na tela é justamente o que não funciona quando há
 * filtro.
 *
 * <p>Sem {@code usuarioId}: o dono sai do token, e devolvê-lo só confirmaria a quem já sabe.
 */
public record RespostaTarefa(
    Long id,
    String titulo,
    String descricao,
    Status status,
    Prioridade prioridade,
    int posicao,
    RespostaCategoria categoria,
    Instant criadaEm,
    Instant dataConclusao
) {

    public static RespostaTarefa aPartirDoDominio(TarefaComCategoria comCategoria) {
        Tarefa tarefa = comCategoria.tarefa();

        return new RespostaTarefa(
            tarefa.getId(),
            tarefa.getTitulo(),
            tarefa.getDescricao(),
            tarefa.getStatus(),
            tarefa.getPrioridade(),
            tarefa.getPosicao(),
            comCategoria.categoria() == null
                ? null
                : RespostaCategoria.aPartirDoDominio(comCategoria.categoria()),
            tarefa.getCriadaEm(),
            tarefa.getDataConclusao()
        );
    }
}
