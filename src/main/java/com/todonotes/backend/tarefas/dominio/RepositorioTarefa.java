package com.todonotes.backend.tarefas.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência das tarefas.
 *
 * <p><b>Toda leitura recebe o {@code usuarioId}, e isso não é repetição.</b> É o que faz
 * "não existe" e "é de outro dono" serem a mesma consulta e a mesma resposta {@code 404}.
 * Uma porta com {@code buscarPorId(Long)} obrigaria o caso de uso a carregar primeiro e
 * conferir o dono depois — e bastaria um esquecimento para abrir a tarefa de outra pessoa.
 * O {@link #excluir(Long)} é a exceção: quem chama já carregou a tarefa pelo par, e repetir
 * a conferência sugeriria que ela não foi feita.
 *
 * <p>Não há método de ordenação nem de renumeração: a ordem é assunto da
 * {@link FaixaDePrioridade}, que devolve as tarefas já com as posições certas, e esta porta
 * só as grava.
 */
public interface RepositorioTarefa {

    Tarefa salvar(Tarefa tarefa);

    /**
     * Grava a faixa renumerada.
     *
     * <p>Escrever todas em qualquer ordem só é seguro porque a restrição
     * {@code uq_tarefas_ordem} é {@code DEFERRABLE INITIALLY DEFERRED}: trocar duas posições
     * passa por um estado em que duas linhas disputam o mesmo lugar, e a conferência adiada
     * para o commit é o que permite esse meio do caminho.
     */
    void salvarTodas(List<Tarefa> tarefas);

    Optional<Tarefa> buscarPorIdEUsuario(Long id, Long usuarioId);

    /**
     * A lista do dono, ordenada por prioridade e posição — a ordem em que a tela a mostra.
     *
     * <p>{@code categoriaId} nulo traz todas. O filtro de concluídas <b>não</b> está aqui:
     * ele depende das {@link Preferencias} e do relógio, e dividi-lo entre a consulta e o
     * caso de uso espalharia por dois lugares uma regra que cabe inteira em um.
     */
    List<Tarefa> listarPorUsuario(Long usuarioId, Long categoriaId);

    /** Uma faixa inteira, em ordem: a matéria-prima da {@link FaixaDePrioridade}. */
    List<Tarefa> listarPorUsuarioEPrioridade(Long usuarioId, Prioridade prioridade);

    /**
     * Quantas tarefas há na faixa — e, por isso, a posição da próxima: as posições são
     * {@code 0..n-1} sem buracos.
     *
     * <p>Existe para que criar uma tarefa seja um {@code INSERT}, e não carregar a faixa
     * inteira, inserir no fim e reescrever tudo para que nada mude de lugar. <b>É também o
     * motivo de a exclusão renumerar a faixa</b>: com um buraco nas posições, esta contagem
     * devolveria um lugar já ocupado.
     */
    int contarPorUsuarioEPrioridade(Long usuarioId, Prioridade prioridade);

    void excluir(Long id);
}
