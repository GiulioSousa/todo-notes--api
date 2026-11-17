package com.todonotes.backend.tarefas.interfaces.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code PUT /api/preferencias}. O {@code PUT} grava as duas chaves de uma vez: são
 * duas, e uma sem a outra não tem significado — dias para ocultar com a opção desligada não
 * muda nada.
 *
 * @param ocultarConcluidasAutomaticamente obrigatório. Objeto, e não {@code boolean}: campo
 *                                         ausente viraria {@code false} em silêncio, ou
 *                                         seja, desligaria a opção de quem só queria mudar
 *                                         os dias.
 * @param diasParaOcultar                  de 1 a 365, o mesmo intervalo do
 *                                         {@code ck_preferencias_dias} e do construtor de
 *                                         {@code Preferencias}. É duração, não dias de
 *                                         calendário: sete dias são 168 horas contadas da
 *                                         conclusão.
 */
public record RequisicaoPreferencias(
    @NotNull(message = "A opção de ocultar automaticamente é obrigatória")
    Boolean ocultarConcluidasAutomaticamente,

    @NotNull(message = "Os dias para ocultar são obrigatórios")
    @Min(value = 1, message = "Os dias para ocultar devem ser no mínimo 1")
    @Max(value = 365, message = "Os dias para ocultar devem ser no máximo 365")
    Integer diasParaOcultar
) {
}
