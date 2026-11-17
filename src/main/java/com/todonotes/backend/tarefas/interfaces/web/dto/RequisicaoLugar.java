package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code PATCH /api/tarefas/{id}/lugar} — a rota do arrastar.
 *
 * <p>Chama-se lugar, e não posição, porque o que muda é o <b>par</b>: arrastar dentro da
 * faixa e arrastar para outra faixa são a mesma operação, e a diferença está aqui.
 *
 * @param prioridade a faixa de destino. Igual à atual, é um movimento dentro da faixa.
 * @param posicao    o índice na lista <b>final</b> da faixa de destino — a convenção do
 *                   {@code currentIndex} do {@code cdk/drag-drop}.
 *                   <p><b>Sem {@code @PositiveOrZero} e sem teto, de propósito.</b> A
 *                   {@code FaixaDePrioridade} ajusta o que estiver fora do intervalo, nas
 *                   duas pontas: o cliente pode estar um passo atrás da realidade, e um
 *                   arrastar que falha por isso é pior que um arrastar que acomoda. Recusar
 *                   o negativo e acomodar o excesso seria arbitrário.
 *                   <p>Objeto, e não primitivo: campo ausente é {@code 400}, não a posição
 *                   zero — que mandaria o card para o topo da faixa sem ninguém pedir.
 */
public record RequisicaoLugar(
    @NotNull(message = "A prioridade é obrigatória")
    Prioridade prioridade,

    @NotNull(message = "A posição é obrigatória")
    Integer posicao
) {
}
