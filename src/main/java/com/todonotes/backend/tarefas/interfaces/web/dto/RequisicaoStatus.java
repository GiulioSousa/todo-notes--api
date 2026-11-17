package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.dominio.Status;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code PATCH /api/tarefas/{id}/status}: o status, e nada mais.
 *
 * <p><b>A data de conclusão nunca aparece em corpo de requisição</b>, em nenhuma rota. Ela é
 * o relógio do servidor — cliente que informa quando algo foi concluído é cliente que mente,
 * por bug ou por mão.
 *
 * @param status o novo status. Valor fora do enum vira {@code 400} na desserialização.
 */
public record RequisicaoStatus(
    @NotNull(message = "O status é obrigatório")
    Status status
) {
}
