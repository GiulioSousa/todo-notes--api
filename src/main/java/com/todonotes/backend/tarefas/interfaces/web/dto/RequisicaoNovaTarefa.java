package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /api/tarefas}.
 *
 * <p>Separado de {@link RequisicaoEdicaoTarefa} porque as duas operações recebem coisas
 * diferentes: a prioridade só existe na criação. Um único DTO para as duas faria o
 * {@code PUT} aceitar um campo que ele ignora — e ignorar em silêncio o que alguém mandou é
 * pior do que não aceitar.
 *
 * @param titulo      obrigatório, limitado ao que a coluna comporta. Sem o teto aqui, o
 *                    excesso chegaria ao banco e a violação do {@code CHECK} voltaria como
 *                    erro interno em vez de {@code 400}.
 * @param descricao   opcional. Em branco chega como nula ao domínio, que é o que a
 *                    {@code ck_tarefas_descricao} espera.
 * @param prioridade  obrigatória, e é ela que diz em qual faixa o card nasce — no fim dela.
 *                    Objeto, e não primitivo: campo ausente tem de virar {@code 400}, não o
 *                    primeiro valor do enum.
 * @param categoriaId opcional, e não cria categoria: id desconhecido é {@code 404}. O
 *                    formulário que aceita nome inédito faz dois chamados (ver a Seção 6).
 */
public record RequisicaoNovaTarefa(
    @NotBlank(message = "O título é obrigatório")
    @Size(max = 120, message = "O título deve ter no máximo 120 caracteres")
    String titulo,

    @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
    String descricao,

    @NotNull(message = "A prioridade é obrigatória")
    Prioridade prioridade,

    Long categoriaId
) {
}
