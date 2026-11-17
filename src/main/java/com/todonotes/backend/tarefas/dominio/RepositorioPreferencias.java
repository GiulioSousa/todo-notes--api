package com.todonotes.backend.tarefas.dominio;

import java.util.Optional;

/**
 * Porta das preferências. Duas operações, porque não há mais nada a fazer com uma linha que
 * é única por dono e nunca é excluída.
 */
public interface RepositorioPreferencias {

    Preferencias salvar(Preferencias preferencias);

    /**
     * Vazio quando o dono nunca gravou nada — situação normal, não erro. Quem responde
     * nesse caso é {@link Preferencias#padrao(Long)}.
     */
    Optional<Preferencias> buscarPorUsuario(Long usuarioId);
}
