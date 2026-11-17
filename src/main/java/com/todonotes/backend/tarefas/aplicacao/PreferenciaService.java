package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Preferencias;
import com.todonotes.backend.tarefas.dominio.RepositorioPreferencias;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As preferências de exibição: ler e gravar, e nada mais.
 *
 * <p>Não há exclusão, e não há {@code 404}: a ausência de linha é respondida com
 * {@link Preferencias#padrao(Long)}.
 */
@Service
public class PreferenciaService {

    private final RepositorioPreferencias repositorioPreferencias;

    public PreferenciaService(RepositorioPreferencias repositorioPreferencias) {
        this.repositorioPreferencias = repositorioPreferencias;
    }

    public Preferencias buscar(Long usuarioId) {
        return repositorioPreferencias.buscarPorUsuario(usuarioId)
            .orElseGet(() -> Preferencias.padrao(usuarioId));
    }

    /**
     * Insere na primeira vez, atualiza nas seguintes, e é um método só.
     *
     * <p>O que distingue os dois casos é o id: o padrão vem sem id, e {@code com(...)} o
     * preserva como está. Um {@code criar} e um {@code atualizar} separados obrigariam a
     * borda a saber se a linha existe — algo que ela não tem como saber antes de perguntar.
     */
    @Transactional
    public Preferencias gravar(Long usuarioId, boolean ocultarConcluidas, int dias) {
        return repositorioPreferencias.salvar(buscar(usuarioId).com(ocultarConcluidas, dias));
    }
}
