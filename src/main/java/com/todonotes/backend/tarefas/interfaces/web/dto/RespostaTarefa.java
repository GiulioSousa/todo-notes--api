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
 * <p><b>As duas posições vêm sempre, e com o nome do agrupamento de cada uma.</b> O card
 * está nas duas ordens ao mesmo tempo — a da faixa de prioridade e a do grupo de categoria
 * —, e é com elas que o cliente monta o próximo {@code PATCH}: a de prioridade para o
 * {@code /lugar}, a de categoria para o {@code /lugar-na-categoria}. Um campo único chamado
 * {@code posicao}, que mudasse de significado conforme a rota que respondeu, obrigaria quem
 * lê o JSON a saber de onde ele veio para entender o número.
 *
 * <p>Deixar o cliente deduzir a posição pelo índice do card na tela é justamente o que não
 * funciona quando há filtro: a tela esconde cards, e a ordem é do grupo inteiro.
 *
 * <p>Sem {@code usuarioId}: o dono sai do token, e devolvê-lo só confirmaria a quem já sabe.
 */
public record RespostaTarefa(
    Long id,
    String titulo,
    String descricao,
    Status status,
    Prioridade prioridade,
    int posicaoNaPrioridade,
    int posicaoNaCategoria,
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
            tarefa.getPosicaoNaPrioridade(),
            tarefa.getPosicaoNaCategoria(),
            comCategoria.categoria() == null
                ? null
                : RespostaCategoria.aPartirDoDominio(comCategoria.categoria()),
            tarefa.getCriadaEm(),
            tarefa.getDataConclusao()
        );
    }
}
