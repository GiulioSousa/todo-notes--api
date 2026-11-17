package com.todonotes.backend.tarefas.interfaces.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /api/categorias} e do {@code PUT /api/categorias/{id}} — aqui um DTO
 * só serve às duas, porque criar e renomear recebem a mesma coisa.
 *
 * @param nome obrigatório, limitado ao teto da coluna. A grafia digitada é a que fica; a
 *             unicidade é conferida sem diferenciar maiúscula de minúscula, e o conflito
 *             volta como {@code 409}.
 */
public record RequisicaoCategoria(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 40, message = "O nome deve ter no máximo 40 caracteres")
    String nome
) {
}
