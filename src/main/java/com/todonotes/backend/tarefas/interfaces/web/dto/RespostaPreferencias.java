package com.todonotes.backend.tarefas.interfaces.web.dto;

import com.todonotes.backend.tarefas.dominio.Preferencias;

/**
 * O que o {@code GET} e o {@code PUT} de preferências devolvem.
 *
 * <p>Sem id e sem {@code usuarioId}: o recurso é singular — {@code /api/preferencias}, sem
 * {@code /{id}} —, e o dono sai do token. Expor o id convidaria a um
 * {@code /api/preferencias/{id}} que não existe e não deve existir.
 */
public record RespostaPreferencias(boolean ocultarConcluidasAutomaticamente, int diasParaOcultar) {

    public static RespostaPreferencias aPartirDoDominio(Preferencias preferencias) {
        return new RespostaPreferencias(
            preferencias.isOcultarConcluidasAutomaticamente(), preferencias.getDiasParaOcultar()
        );
    }
}
