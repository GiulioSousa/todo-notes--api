package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.GrupoDeCategoria;
import com.todonotes.backend.tarefas.dominio.RepositorioCategoria;
import com.todonotes.backend.tarefas.dominio.RepositorioTarefa;
import com.todonotes.backend.tarefas.dominio.Tarefa;
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
 * <p>Têm duas portas de entrada: a aba de categorias, que faz as quatro, e o formulário da
 * tarefa, que cria uma categoria nova antes de salvar o card (ver a Seção 6).
 *
 * <p><b>Depende do repositório de tarefas por causa da exclusão</b>, e isso não é vazamento
 * de responsabilidade: excluir uma categoria mexe na ordem do grupo das tarefas sem etiqueta,
 * que é um dado das tarefas. Está explicado no {@link #excluir}.
 */
@Service
public class CategoriaService {

    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioTarefa repositorioTarefa;
    private final Clock relogio;

    public CategoriaService(
        RepositorioCategoria repositorioCategoria,
        RepositorioTarefa repositorioTarefa,
        Clock relogio
    ) {
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioTarefa = repositorioTarefa;
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
     *
     * <p><b>E põe as órfãs no fim do grupo das sem etiqueta</b>, o que não é acabamento: é o
     * que impede a exclusão de falhar. As tarefas que perdem a etiqueta caem no grupo das sem
     * categoria levando as posições que tinham no grupo que deixou de existir — posições que
     * já estão ocupadas lá —, e a restrição {@code uq_tarefas_ordem_na_categoria} aborta a
     * transação no commit. Está demonstrado no {@code db/conferir-v2.sql}, conferências 5 e 6:
     * o mesmo {@code DELETE} falha sem a renumeração e passa com ela.
     *
     * <p>A ordem relativa das órfãs é preservada, e elas entram <b>depois</b> das que já
     * estavam sem etiqueta: excluir uma categoria não é motivo para reordenar o que ninguém
     * tocou.
     *
     * <p>Qual das duas escritas o Hibernate manda primeiro não importa, e isso é a restrição
     * deferida trabalhando de novo: se o {@code DELETE} for primeiro, o {@code SET NULL} do
     * banco cria as posições repetidas e os {@code UPDATE} as desfazem antes do commit; se
     * forem os {@code UPDATE}, a categoria já não tem tarefa nenhuma quando a linha dela sai.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        buscarOuLancar(usuarioId, id);

        List<Tarefa> orfas = repositorioTarefa.listarPorUsuarioECategoria(usuarioId, id);
        GrupoDeCategoria semEtiqueta = GrupoDeCategoria
            .de(null, repositorioTarefa.listarPorUsuarioECategoria(usuarioId, null))
            .inserirTodasNoFim(orfas);

        repositorioCategoria.excluir(id);
        repositorioTarefa.salvarTodas(semEtiqueta.tarefas());
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
