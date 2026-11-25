package com.todonotes.backend.tarefas.interfaces.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code PATCH /api/tarefas/{id}/lugar-na-categoria} — o arrastar no modo agrupado
 * por categoria.
 *
 * <p>É o par do {@link RequisicaoLugar} na outra ordem, e muda o mesmo tipo de coisa: o
 * <b>par</b> grupo e posição. Arrastar dentro do grupo e arrastar para outro grupo são a
 * mesma operação.
 *
 * @param categoriaId a categoria de destino, ou <b>{@code null} para o grupo das tarefas sem
 *                    etiqueta</b> — soltar um card ali é como se tira a categoria de uma
 *                    tarefa sem abrir o formulário.
 *                    <p><b>Sem {@code @NotNull}</b>, ao contrário da prioridade do
 *                    {@link RequisicaoLugar}: aqui o nulo é um destino, e não a ausência de
 *                    um. O preço é que corpo sem o campo vale como "sem etiqueta" em vez de
 *                    {@code 400}, e o JSON não tem como distinguir os dois — é a mesma
 *                    convenção do {@code categoriaId} na criação e na edição.
 * @param posicao     o índice na lista <b>final</b> do grupo de destino — a convenção do
 *                    {@code currentIndex} do {@code cdk/drag-drop}.
 *                    <p>Sem {@code @PositiveOrZero} e sem teto, de propósito: o
 *                    {@code GrupoDeCategoria} ajusta o que estiver fora do intervalo, nas
 *                    duas pontas, porque o cliente pode estar um passo atrás da realidade.
 *                    <p>Objeto, e não primitivo: campo ausente é {@code 400}, não a posição
 *                    zero — que mandaria o card para o topo do grupo sem ninguém pedir.
 */
public record RequisicaoLugarNaCategoria(
    Long categoriaId,

    @NotNull(message = "A posição é obrigatória")
    Integer posicao
) {
}
