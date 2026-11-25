package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Prioridade;
import com.todonotes.backend.tarefas.dominio.RepositorioTarefa;
import com.todonotes.backend.tarefas.dominio.Tarefa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cumpre a porta {@link RepositorioTarefa} com Spring Data.
 *
 * <p>Traduz entidade para domínio na saída e domínio para entidade na entrada, e é isso que
 * mantém o JPA deste lado da fronteira: o caso de uso recebe domínio, não objeto gerenciado
 * pelo Hibernate — e com isso nenhuma alteração sua vira {@code UPDATE} por conta própria no
 * fim da transação.
 */
@Repository
public class RepositorioTarefaJpaAdapter implements RepositorioTarefa {

    private final TarefaSpringDataRepository springDataRepository;

    public RepositorioTarefaJpaAdapter(TarefaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Tarefa salvar(Tarefa tarefa) {
        return springDataRepository.save(TarefaJpaEntity.deDominio(tarefa)).paraDominio();
    }

    /**
     * Grava o grupo renumerado de uma vez.
     *
     * <p>Não devolve nada de propósito: quem chamou já tem as tarefas com as posições
     * certas — elas vieram de um {@code GrupoDeTarefas} —, e devolver as entidades
     * recém-mescladas só daria a impressão de que o banco mudou algo.
     */
    @Override
    public void salvarTodas(List<Tarefa> tarefas) {
        springDataRepository.saveAll(tarefas.stream().map(TarefaJpaEntity::deDominio).toList());
    }

    @Override
    public Optional<Tarefa> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId)
            .map(TarefaJpaEntity::paraDominio);
    }

    /**
     * Dois caminhos, e não um {@code WHERE} com comparação a nulo — ver o repositório.
     *
     * <p>Aqui o nulo é <b>filtro ausente</b>: traz todas as categorias. Não confundir com o
     * {@link #listarPorUsuarioECategoria}, onde o nulo é uma chave.
     */
    @Override
    public List<Tarefa> listarPorUsuario(Long usuarioId, Long categoriaId) {
        List<TarefaJpaEntity> encontradas = categoriaId == null
            ? springDataRepository.findByUsuarioIdOrderByPrioridadeAscPosicaoNaPrioridadeAsc(usuarioId)
            : springDataRepository
                .findByUsuarioIdAndCategoriaIdOrderByPrioridadeAscPosicaoNaPrioridadeAsc(
                    usuarioId, categoriaId);

        return paraDominio(encontradas);
    }

    @Override
    public List<Tarefa> listarPorUsuarioOrdenadoPorCategoria(Long usuarioId) {
        return paraDominio(
            springDataRepository.findByUsuarioIdOrderByCategoriaIdAscPosicaoNaCategoriaAsc(usuarioId)
        );
    }

    @Override
    public List<Tarefa> listarPorUsuarioEPrioridade(Long usuarioId, Prioridade prioridade) {
        return paraDominio(
            springDataRepository.findByUsuarioIdAndPrioridadeOrderByPosicaoNaPrioridadeAsc(
                usuarioId, prioridade)
        );
    }

    /** Aqui o nulo é a <b>chave</b> do grupo das sem etiqueta, e não filtro ausente. */
    @Override
    public List<Tarefa> listarPorUsuarioECategoria(Long usuarioId, Long categoriaId) {
        List<TarefaJpaEntity> encontradas = categoriaId == null
            ? springDataRepository.findByUsuarioIdAndCategoriaIdIsNullOrderByPosicaoNaCategoriaAsc(
                usuarioId)
            : springDataRepository.findByUsuarioIdAndCategoriaIdOrderByPosicaoNaCategoriaAsc(
                usuarioId, categoriaId);

        return paraDominio(encontradas);
    }

    @Override
    public int contarPorUsuarioEPrioridade(Long usuarioId, Prioridade prioridade) {
        return springDataRepository.countByUsuarioIdAndPrioridade(usuarioId, prioridade);
    }

    @Override
    public int contarPorUsuarioECategoria(Long usuarioId, Long categoriaId) {
        return categoriaId == null
            ? springDataRepository.countByUsuarioIdAndCategoriaIdIsNull(usuarioId)
            : springDataRepository.countByUsuarioIdAndCategoriaId(usuarioId, categoriaId);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    private static List<Tarefa> paraDominio(List<TarefaJpaEntity> entidades) {
        return entidades.stream().map(TarefaJpaEntity::paraDominio).toList();
    }
}
