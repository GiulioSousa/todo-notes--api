package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.FaixaDePrioridade;
import com.todonotes.backend.tarefas.dominio.FiltroDeConcluidas;
import com.todonotes.backend.tarefas.dominio.Preferencias;
import com.todonotes.backend.tarefas.dominio.Prioridade;
import com.todonotes.backend.tarefas.dominio.RepositorioCategoria;
import com.todonotes.backend.tarefas.dominio.RepositorioPreferencias;
import com.todonotes.backend.tarefas.dominio.RepositorioTarefa;
import com.todonotes.backend.tarefas.dominio.Status;
import com.todonotes.backend.tarefas.dominio.Tarefa;
import com.todonotes.backend.tarefas.dominio.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Os casos de uso das tarefas.
 *
 * <p>Não há regra de ordenação aqui: toda ela está na {@link FaixaDePrioridade}, e este
 * serviço só carrega a faixa, chama um método dela e manda gravar o que voltou. O que é
 * dele: conferir o dono, resolver a categoria, decidir o que a tela vê e abrir a transação.
 *
 * <p><b>A transação não é detalhe.</b> A restrição {@code uq_tarefas_ordem} é
 * {@code DEFERRABLE INITIALLY DEFERRED}, e é no commit que o banco confere as posições.
 * Qualquer operação que renumere uma faixa fora de uma transação falharia na primeira troca
 * de lugar — por isso {@code @Transactional} está em todo método que escreve, e não só nos
 * que escrevem em duas tabelas.
 */
@Service
public class TarefaService {

    private final RepositorioTarefa repositorioTarefa;
    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioPreferencias repositorioPreferencias;

    /**
     * Relógio injetado, e não {@code Instant.now()} espalhado pelos métodos, porque dois
     * comportamentos dependem do tempo e precisam de teste: a data de conclusão e a regra de
     * ocultar depois de X dias. Testar a segunda com o relógio do sistema pediria esperar
     * sete dias.
     */
    private final Clock relogio;

    public TarefaService(
        RepositorioTarefa repositorioTarefa,
        RepositorioCategoria repositorioCategoria,
        RepositorioPreferencias repositorioPreferencias,
        Clock relogio
    ) {
        this.repositorioTarefa = repositorioTarefa;
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioPreferencias = repositorioPreferencias;
        this.relogio = relogio;
    }

    /**
     * A lista, nas três faixas — <b>sempre as três</b>, mesmo vazias: faixa vazia continua
     * sendo área de soltura do arrastar, e omiti-la obrigaria o frontend a inventar a lista
     * de prioridades para desenhar o alvo.
     *
     * <p>A ordem dentro de cada faixa é a que o repositório devolveu, por posição. O
     * agrupamento preserva essa ordem porque apenas separa a lista já ordenada.
     */
    public List<FaixaExibida> listar(Long usuarioId, Long categoriaId, FiltroDeConcluidas concluidas) {
        List<Tarefa> visiveis = aplicarFiltro(
            usuarioId, repositorioTarefa.listarPorUsuario(usuarioId, categoriaId), concluidas
        );
        Map<Long, Categoria> categorias = categoriasPorId(usuarioId);

        List<FaixaExibida> faixas = new ArrayList<>(Prioridade.values().length);
        for (Prioridade prioridade : Prioridade.values()) {
            faixas.add(new FaixaExibida(prioridade, visiveis.stream()
                .filter(tarefa -> tarefa.getPrioridade() == prioridade)
                .map(tarefa -> comCategoria(tarefa, categorias))
                .toList()));
        }
        return faixas;
    }

    /**
     * Cria <b>no fim da faixa</b>: tarefa nova não passa na frente do que já foi ordenado.
     *
     * <p>A posição vem de uma contagem, e não de carregar a faixa inteira para inserir no
     * fim: as posições são {@code 0..n-1} sem buracos, então a contagem <b>é</b> a próxima
     * posição, e criar fica em um {@code INSERT} em vez de um {@code INSERT} mais um
     * {@code UPDATE} por card que não mudou de lugar.
     *
     * <p>Categoria nova não se cria por aqui (ver a Seção 6): {@code categoriaId}
     * desconhecido é {@code 404}, não convite a cadastrar.
     */
    @Transactional
    public TarefaComCategoria criar(
        Long usuarioId, String titulo, String descricao, Prioridade prioridade, Long categoriaId
    ) {
        Categoria categoria = exigirCategoria(categoriaId, usuarioId);
        int posicao = repositorioTarefa.contarPorUsuarioEPrioridade(usuarioId, prioridade);

        Tarefa nova = Tarefa.nova(
            titulo, descricao, prioridade, categoriaId, usuarioId, posicao, relogio.instant()
        );
        return new TarefaComCategoria(repositorioTarefa.salvar(nova), categoria);
    }

    /**
     * Edita título, descrição e categoria — e só. Prioridade e posição mudam por
     * {@link #mover}, status por {@link #alterarStatus}: é o que mantém a regra da faixa num
     * lugar só.
     */
    @Transactional
    public TarefaComCategoria editar(
        Long usuarioId, Long id, String titulo, String descricao, Long categoriaId
    ) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        Categoria categoria = exigirCategoria(categoriaId, usuarioId);

        Tarefa editada = repositorioTarefa.salvar(tarefa.comDados(titulo, descricao, categoriaId));
        return new TarefaComCategoria(editada, categoria);
    }

    /**
     * O arrastar. Devolve as faixas afetadas — uma, quando o card fica na mesma prioridade;
     * duas, quando troca —, para o cliente não precisar recarregar a lista nem confiar na
     * própria conta de posições.
     *
     * <p>Mudar de faixa é remover de uma e inserir na outra, e a prioridade do card passa a
     * ser a da faixa de destino sem que este método precise ajustá-la: quem a impõe é o
     * {@code inserir}.
     *
     * <p>A ordem em que as duas faixas são gravadas não importa, e isso também é a restrição
     * deferida trabalhando: no meio da escrita há posições repetidas entre o que já foi
     * gravado e o que ainda não foi.
     *
     * <p><b>As faixas voltam sem filtro</b>, inteiras. Enquanto o arrastar só for permitido
     * na lista sem filtro, é a mesma coisa; se a decisão registrada em "Lacunas e riscos
     * conhecidos" do {@code execucao.md} for outra, o filtro precisa chegar até aqui.
     */
    @Transactional
    public List<FaixaExibida> mover(Long usuarioId, Long id, Prioridade prioridade, int posicao) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        Prioridade origem = tarefa.getPrioridade();

        List<FaixaDePrioridade> afetadas = origem == prioridade
            ? List.of(carregarFaixa(usuarioId, prioridade).mover(id, posicao))
            : List.of(
                carregarFaixa(usuarioId, origem).remover(id),
                carregarFaixa(usuarioId, prioridade).inserir(tarefa, posicao)
            );

        afetadas.forEach(faixa -> repositorioTarefa.salvarTodas(faixa.tarefas()));

        Map<Long, Categoria> categorias = categoriasPorId(usuarioId);
        return afetadas.stream()
            .sorted(Comparator.comparing(FaixaDePrioridade::prioridade))
            .map(faixa -> new FaixaExibida(faixa.prioridade(), faixa.tarefas().stream()
                .map(cadaTarefa -> comCategoria(cadaTarefa, categorias))
                .toList()))
            .toList();
    }

    /**
     * Muda o status. Quem preenche ou limpa a data de conclusão é o domínio, com o relógio
     * do servidor — o cliente manda o status e nada mais.
     *
     * <p>Repetir o status atual não grava nada: {@code comStatus} devolve a própria
     * instância, e é essa identidade que este método confere. Sem isso, concluir duas vezes
     * reescreveria a data da primeira conclusão, que é o que o registro de concluídas existe
     * para guardar.
     */
    @Transactional
    public TarefaComCategoria alterarStatus(Long usuarioId, Long id, Status status) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        Tarefa alterada = tarefa.comStatus(status, relogio.instant());

        if (alterada != tarefa) {
            alterada = repositorioTarefa.salvar(alterada);
        }
        return comCategoria(alterada, usuarioId);
    }

    /**
     * Exclui definitivamente — não há lixeira (ver "Fora do MVP").
     *
     * <p><b>E renumera a faixa.</b> Não é zelo: sem isso as posições ficam com buraco
     * (0, 1, 3), e a contagem que o {@link #criar} usa para achar o fim da faixa devolveria
     * um lugar já ocupado — a próxima tarefa criada esbarraria na restrição única, no commit,
     * e o erro apareceria longe da exclusão que o causou.
     *
     * <p>A faixa é carregada <b>antes</b> do {@code excluir}: depois, a linha já não está
     * lá para ser retirada da lista.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        FaixaDePrioridade faixa = carregarFaixa(usuarioId, tarefa.getPrioridade());

        repositorioTarefa.excluir(id);
        repositorioTarefa.salvarTodas(faixa.remover(id).tarefas());
    }

    /**
     * Aplica a regra de concluídas. É aqui, e não na consulta, porque ela depende das
     * preferências e do relógio — e porque {@code PADRAO} é a única das três que precisa
     * consultar as preferências.
     */
    private List<Tarefa> aplicarFiltro(
        Long usuarioId, List<Tarefa> tarefas, FiltroDeConcluidas concluidas
    ) {
        return switch (concluidas) {
            case TODAS -> tarefas;
            case NENHUMA -> tarefas.stream()
                .filter(tarefa -> !tarefa.getStatus().concluida())
                .toList();
            case PADRAO -> {
                Preferencias preferencias = repositorioPreferencias.buscarPorUsuario(usuarioId)
                    .orElseGet(() -> Preferencias.padrao(usuarioId));

                if (!preferencias.isOcultarConcluidasAutomaticamente()) {
                    yield tarefas;
                }

                Instant agora = relogio.instant();
                yield tarefas.stream()
                    .filter(tarefa -> !preferencias.ocultaTarefa(tarefa, agora))
                    .toList();
            }
        };
    }

    private Tarefa buscarOuLancar(Long usuarioId, Long id) {
        return repositorioTarefa.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(RecursoNaoEncontradoException::tarefa);
    }

    private FaixaDePrioridade carregarFaixa(Long usuarioId, Prioridade prioridade) {
        return FaixaDePrioridade.de(
            prioridade, repositorioTarefa.listarPorUsuarioEPrioridade(usuarioId, prioridade)
        );
    }

    /** {@code null} é tarefa sem etiqueta; id que não é do dono é {@code 404}. */
    private Categoria exigirCategoria(Long categoriaId, Long usuarioId) {
        if (categoriaId == null) {
            return null;
        }
        return repositorioCategoria.buscarPorIdEUsuario(categoriaId, usuarioId)
            .orElseThrow(RecursoNaoEncontradoException::categoria);
    }

    /** Uma consulta para a lista inteira, em vez de uma por card. */
    private Map<Long, Categoria> categoriasPorId(Long usuarioId) {
        return repositorioCategoria.listarPorUsuario(usuarioId).stream()
            .collect(Collectors.toMap(Categoria::getId, categoria -> categoria));
    }

    private TarefaComCategoria comCategoria(Tarefa tarefa, Map<Long, Categoria> categorias) {
        return new TarefaComCategoria(tarefa, categorias.get(tarefa.getCategoriaId()));
    }

    /**
     * Para uma tarefa só, sem carregar a lista de categorias.
     *
     * <p>Categoria ausente volta nula em vez de lançar: se a etiqueta foi excluída entre a
     * leitura e aqui, o que a pessoa pediu — mudar o status — deu certo, e falhar por causa
     * do nome da etiqueta seria inventar um erro.
     */
    private TarefaComCategoria comCategoria(Tarefa tarefa, Long usuarioId) {
        if (tarefa.getCategoriaId() == null) {
            return new TarefaComCategoria(tarefa, null);
        }
        return new TarefaComCategoria(
            tarefa,
            repositorioCategoria.buscarPorIdEUsuario(tarefa.getCategoriaId(), usuarioId).orElse(null)
        );
    }
}
