package com.todonotes.backend.tarefas.interfaces.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code PUT /api/tarefas/{id}}: o que o formulário de edição edita.
 *
 * <p>Sem prioridade e sem posição — mudar de faixa pelo formulário cai no
 * {@code PATCH /api/tarefas/{id}/lugar}, com a posição do fim da faixa de destino. É o que
 * mantém a regra da faixa num lugar só. Sem status, também: ele tem rota própria.
 *
 * @param titulo      obrigatório; os mesmos limites da criação.
 * @param descricao   opcional. Mandar nula ou em branco apaga a que havia.
 * @param categoriaId opcional. Nulo <b>desetiqueta</b> a tarefa, e é assim que se tira a
 *                    categoria sem excluí-la.
 */
public record RequisicaoEdicaoTarefa(
    @NotBlank(message = "O título é obrigatório")
    @Size(max = 120, message = "O título deve ter no máximo 120 caracteres")
    String titulo,

    @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
    String descricao,

    Long categoriaId
) {
}
