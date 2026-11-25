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
 * <p>Não há método de ordenação nem de renumeração: a ordem é assunto dos
 * {@link GrupoDeTarefas}, que devolvem as tarefas já com as posições certas — as duas, uma
 * por agrupamento —, e esta porta só as grava.
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

    /**
     * A lista do dono na ordem do <b>outro</b> agrupamento: por categoria e, dentro dela,
     * pela posição do grupo.
     *
     * <p>É uma consulta à parte, e não a de cima reordenada em memória, pelo mesmo motivo
     * que a de cima existe: a restrição {@code uq_tarefas_ordem_na_categoria} é também o
     * índice desta ordenação, e o banco a entrega na mesma passagem da leitura.
     *
     * <p>Quem ordena os grupos entre si é o caso de uso, por nome de categoria — aqui eles
     * saem por {@code categoria_id}, que não quer dizer nada para quem lê a tela.
     */
    List<Tarefa> listarPorUsuarioOrdenadoPorCategoria(Long usuarioId);

    /** Uma faixa inteira, em ordem: a matéria-prima da {@link FaixaDePrioridade}. */
    List<Tarefa> listarPorUsuarioEPrioridade(Long usuarioId, Prioridade prioridade);

    /**
     * Um grupo de categoria inteiro, em ordem: a matéria-prima do {@link GrupoDeCategoria}.
     *
     * <p>{@code categoriaId} nulo traz o grupo das tarefas sem etiqueta — e traz <b>esse</b>
     * grupo, não todas as tarefas. É a diferença entre a chave ser nula e o filtro estar
     * ausente, e é por isso que esta porta tem um método só para ela em vez de reaproveitar
     * o {@link #listarPorUsuario}.
     */
    List<Tarefa> listarPorUsuarioECategoria(Long usuarioId, Long categoriaId);

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

    /**
     * O mesmo para o grupo de categoria: quantas tarefas há nele, e por isso o lugar da
     * próxima. {@code categoriaId} nulo conta o grupo das sem etiqueta.
     *
     * <p>Criar uma tarefa precisa das duas contagens, porque ela nasce no fim dos dois
     * grupos a que passa a pertencer.
     */
    int contarPorUsuarioECategoria(Long usuarioId, Long categoriaId);

    void excluir(Long id);
}
