package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.RepositorioCategoria;
import com.todonotes.backend.tarefas.dominio.excecoes.CategoriaDuplicadaException;
import com.todonotes.backend.tarefas.dominio.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Os casos de uso das categorias: listar, criar, renomear, excluir.
 *
 * <p>Não há tela dedicada a elas no MVP — a criação acontece de dentro do formulário da
 * tarefa, em dois chamados (ver a Seção 6). Estas quatro operações existem para que isso
 * baste.
 */
@Service
public class CategoriaService {

    private final RepositorioCategoria repositorioCategoria;
    private final Clock relogio;

    public CategoriaService(RepositorioCategoria repositorioCategoria, Clock relogio) {
        this.repositorioCategoria = repositorioCategoria;
        this.relogio = relogio;
    }

    public List<Categoria> listar(Long usuarioId) {
        return repositorioCategoria.listarPorUsuario(usuarioId);
    }

    @Transactional
    public Categoria criar(Long usuarioId, String nome) {
        Categoria nova = Categoria.nova(nome, usuarioId, relogio.instant());
        exigirNomeLivre(nova.getNome(), usuarioId, null);
        return repositorioCategoria.salvar(nova);
    }

    @Transactional
    public Categoria renomear(Long usuarioId, Long id, String nome) {
        Categoria categoria = buscarOuLancar(usuarioId, id);
        Categoria renomeada = categoria.comNome(nome);

        exigirNomeLivre(renomeada.getNome(), usuarioId, id);
        return repositorioCategoria.salvar(renomeada);
    }

    /**
     * Exclui sem bloquear, mesmo em uso: as tarefas perdem a etiqueta pelo
     * {@code ON DELETE SET NULL (categoria_id)}. É o oposto do que o GeFinX faz com conta em
     * uso, e de propósito — categoria é etiqueta, não dado de que outro dado depende.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        buscarOuLancar(usuarioId, id);
        repositorioCategoria.excluir(id);
    }

    /**
     * Confere o nome <b>sem diferenciar maiúscula de minúscula</b>, a regra do índice
     * {@code uq_categorias_nome_por_usuario}. O {@code idPermitido} é o da própria categoria
     * ao renomear: sem ele, corrigir {@code casa} para {@code Casa} daria {@code 409}
     * contra ela mesma.
     *
     * <p>Duas criações simultâneas do mesmo nome ainda esbarrariam no índice, e não aqui.
     * Num projeto de um usuário só, essa corrida não vale o trabalho de fechar — o banco
     * continua sendo a última palavra.
     */
    private void exigirNomeLivre(String nome, Long usuarioId, Long idPermitido) {
        repositorioCategoria.buscarPorNomeEUsuario(nome, usuarioId)
            .filter(existente -> !Objects.equals(existente.getId(), idPermitido))
            .ifPresent(existente -> {
                throw new CategoriaDuplicadaException(nome);
            });
    }

    private Categoria buscarOuLancar(Long usuarioId, Long id) {
        return repositorioCategoria.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(RecursoNaoEncontradoException::categoria);
    }
}
