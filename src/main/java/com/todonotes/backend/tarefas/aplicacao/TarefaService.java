package com.todonotes.backend.tarefas.aplicacao;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.FaixaDePrioridade;
import com.todonotes.backend.tarefas.dominio.FiltroDeConcluidas;
import com.todonotes.backend.tarefas.dominio.GrupoDeCategoria;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Os casos de uso das tarefas.
 *
 * <p>Não há regra de ordenação aqui: toda ela está nos {@link com.todonotes.backend.tarefas.dominio.GrupoDeTarefas},
 * e este serviço só carrega o grupo, chama um método dele e manda gravar o que voltou. O que
 * é dele: conferir o dono, resolver a categoria, decidir o que a tela vê e abrir a transação.
 *
 * <p><b>Dois modos de visualização, duas ordens.</b> A lista pode ser agrupada por prioridade
 * ou por categoria, e cada agrupamento guarda a sua própria ordem — arrastar num deles não
 * mexe no outro. Por isso há dois métodos de listar e dois de mover, e não um com um
 * parâmetro: o que muda não é um detalhe da consulta, é qual das duas ordens está sendo
 * lida ou reescrita.
 *
 * <p><b>A transação não é detalhe.</b> As restrições {@code uq_tarefas_ordem_na_prioridade} e
 * {@code uq_tarefas_ordem_na_categoria} são {@code DEFERRABLE INITIALLY DEFERRED}, e é no
 * commit que o banco confere as posições. Qualquer operação que renumere um grupo fora de uma
 * transação falharia na primeira troca de lugar — por isso {@code @Transactional} está em
 * todo método que escreve, e não só nos que escrevem em duas tabelas.
 */
@Service
public class TarefaService {

    /**
     * A ordem em que os grupos de categoria aparecem: pelo nome, e o das tarefas sem etiqueta
     * por último — ele é o único que não é escolha de ninguém, e pô-lo no meio do alfabeto
     * faria a lista parecer ter uma categoria chamada "sem categoria".
     */
    private static final Comparator<GrupoExibido> ORDEM_DOS_GRUPOS = Comparator.comparing(
        (GrupoExibido grupo) -> grupo.categoria() == null ? null : grupo.categoria().getNome(),
        Comparator.nullsLast(Comparator.<String>naturalOrder())
    );

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
     * A lista agrupada por prioridade, nas três faixas — <b>sempre as três</b>, mesmo vazias:
     * faixa vazia continua sendo área de soltura do arrastar, e omiti-la obrigaria o frontend
     * a inventar a lista de prioridades para desenhar o alvo.
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
     * A lista agrupada por categoria, com <b>um grupo por categoria cadastrada</b> — mesmo
     * vazia, pelo mesmo motivo das três faixas — e o grupo das tarefas sem etiqueta no fim.
     *
     * <p>Os grupos saem na ordem dos nomes, que é a ordem em que o repositório de categorias
     * os devolve; o das sem etiqueta vai por último porque é o único que não é uma escolha de
     * ninguém. Dentro de cada grupo, a ordem é a {@code posicaoNaCategoria}, que o banco
     * entrega pelo índice.
     *
     * <p>Não aceita filtro de categoria, e isso é de propósito: filtrar por categoria uma
     * lista agrupada por categoria é escolher um grupo, e quem o faz é a tela, rolando até
     * ele.
     */
    public List<GrupoExibido> listarPorCategoria(Long usuarioId, FiltroDeConcluidas concluidas) {
        List<Tarefa> visiveis = aplicarFiltro(
            usuarioId, repositorioTarefa.listarPorUsuarioOrdenadoPorCategoria(usuarioId), concluidas
        );
        return agruparPorCategoria(visiveis, repositorioCategoria.listarPorUsuario(usuarioId));
    }

    /**
     * Cria <b>no fim dos dois grupos</b> a que a tarefa passa a pertencer: o da prioridade
     * escolhida e o da categoria escolhida. Tarefa nova não passa na frente do que já foi
     * ordenado, em nenhum dos dois modos de visualização.
     *
     * <p>As duas posições vêm de contagens, e não de carregar os grupos inteiros para inserir
     * no fim: as posições são {@code 0..n-1} sem buracos, então a contagem <b>é</b> a próxima
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

        Tarefa nova = Tarefa.nova(
            titulo, descricao, prioridade, categoriaId, usuarioId,
            repositorioTarefa.contarPorUsuarioEPrioridade(usuarioId, prioridade),
            repositorioTarefa.contarPorUsuarioECategoria(usuarioId, categoriaId),
            relogio.instant()
        );
        return new TarefaComCategoria(repositorioTarefa.salvar(nova), categoria);
    }

    /**
     * Edita título, descrição e categoria. Prioridade e posições mudam por {@link #mover} e
     * {@link #moverNaCategoria}, status por {@link #alterarStatus}.
     *
     * <p><b>Trocar a categoria aqui move o card</b>, para o fim do grupo de destino — não dá
     * para mudar a etiqueta sem mudar de grupo, porque a etiqueta <b>é</b> a chave do grupo
     * no outro modo de visualização. É a mesma razão pela qual trocar a prioridade no
     * formulário cai no {@link #mover}: a regra do grupo fica num lugar só.
     *
     * <p>A ordem por prioridade não é tocada: o card continua na mesma faixa, no mesmo lugar.
     */
    @Transactional
    public TarefaComCategoria editar(
        Long usuarioId, Long id, String titulo, String descricao, Long categoriaId
    ) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        Categoria categoria = exigirCategoria(categoriaId, usuarioId);
        Tarefa editada = tarefa.comDados(titulo, descricao);

        if (Objects.equals(categoriaId, tarefa.getCategoriaId())) {
            return new TarefaComCategoria(repositorioTarefa.salvar(editada), categoria);
        }

        GrupoDeCategoria origem = carregarGrupo(usuarioId, tarefa.getCategoriaId()).remover(id);
        GrupoDeCategoria destino = carregarGrupo(usuarioId, categoriaId).inserirNoFim(editada);

        repositorioTarefa.salvarTodas(origem.tarefas());
        repositorioTarefa.salvarTodas(destino.tarefas());

        return new TarefaComCategoria(exigirNoGrupo(destino, id), categoria);
    }

    /**
     * O arrastar no modo por prioridade. Devolve as faixas afetadas — uma, quando o card fica
     * na mesma prioridade; duas, quando troca —, para o cliente não precisar recarregar a
     * lista nem confiar na própria conta de posições.
     *
     * <p>Mudar de faixa é remover de uma e inserir na outra, e a prioridade do card passa a
     * ser a da faixa de destino sem que este método precise ajustá-la: quem a impõe é o
     * {@code inserir}.
     *
     * <p>A ordem em que as duas faixas são gravadas não importa, e isso também é a restrição
     * deferida trabalhando: no meio da escrita há posições repetidas entre o que já foi
     * gravado e o que ainda não foi.
     *
     * <p><b>A ordem por categoria não é tocada</b>, e é justamente isso que os dois modos
     * prometem: o card muda de faixa e continua no mesmo lugar do grupo da categoria dele.
     *
     * <p><b>As faixas voltam sem filtro</b>, inteiras: esta rota não conhece os filtros da
     * tela. O cliente descarta da resposta os cards que não estavam à vista — é o que o
     * {@code substituirFaixas} do frontend faz.
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
     * O arrastar no modo por categoria — o mesmo movimento do {@link #mover}, na outra ordem.
     *
     * <p><b>Soltar um card em outro grupo troca a etiqueta dele</b>, inclusive para nenhuma:
     * {@code categoriaId} nulo é o grupo das sem etiqueta, e arrastar para lá é como se tira
     * a categoria de uma tarefa sem abrir o formulário. Quem impõe a categoria é o
     * {@code inserir} do grupo de destino, pela mesma razão de a faixa impor a prioridade.
     *
     * <p>A ordem por prioridade não é tocada: o card continua na faixa dele, no mesmo lugar.
     *
     * <p>Devolve os grupos afetados, inteiros e sem filtro, no mesmo formato da listagem.
     */
    @Transactional
    public List<GrupoExibido> moverNaCategoria(
        Long usuarioId, Long id, Long categoriaId, int posicao
    ) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);
        exigirCategoria(categoriaId, usuarioId);
        Long origem = tarefa.getCategoriaId();

        List<GrupoDeCategoria> afetados = Objects.equals(origem, categoriaId)
            ? List.of(carregarGrupo(usuarioId, categoriaId).mover(id, posicao))
            : List.of(
                carregarGrupo(usuarioId, origem).remover(id),
                carregarGrupo(usuarioId, categoriaId).inserir(tarefa, posicao)
            );

        afetados.forEach(grupo -> repositorioTarefa.salvarTodas(grupo.tarefas()));

        Map<Long, Categoria> categorias = categoriasPorId(usuarioId);
        return afetados.stream()
            .map(grupo -> new GrupoExibido(
                grupo.categoriaId() == null ? null : categorias.get(grupo.categoriaId()),
                grupo.tarefas().stream()
                    .map(cadaTarefa -> comCategoria(cadaTarefa, categorias))
                    .toList()))
            .sorted(ORDEM_DOS_GRUPOS)
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
     * <p><b>E renumera os dois grupos</b>, a faixa e o de categoria. Não é zelo: sem isso as
     * posições ficam com buraco (0, 1, 3), e as contagens que o {@link #criar} usa para achar
     * o fim de cada grupo devolveriam um lugar já ocupado — a próxima tarefa criada esbarraria
     * na restrição única, no commit, e o erro apareceria longe da exclusão que o causou.
     *
     * <p><b>O segundo grupo é montado sobre o resultado do primeiro</b>, e essa ordem é o
     * ponto delicado deste método. Cada grupo escreve só a posição que é dele; uma tarefa que
     * esteja nos dois — mesma prioridade e mesma categoria da excluída — seria gravada duas
     * vezes, e a segunda gravação levaria a posição que a primeira acabou de mudar, desfazendo
     * a renumeração. Montar o grupo de categoria com as instâncias que a faixa já devolveu faz
     * a linha sair com as duas posições novas.
     *
     * <p>Os grupos são carregados <b>antes</b> do {@code excluir}: depois, a linha já não está
     * lá para ser retirada da lista.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Tarefa tarefa = buscarOuLancar(usuarioId, id);

        List<Tarefa> comAFaixaRenumerada =
            carregarFaixa(usuarioId, tarefa.getPrioridade()).remover(id).tarefas();

        List<Tarefa> comAsDuasRenumeradas = GrupoDeCategoria.de(
            tarefa.getCategoriaId(),
            comAsVersoesMaisNovas(
                repositorioTarefa.listarPorUsuarioECategoria(usuarioId, tarefa.getCategoriaId()),
                comAFaixaRenumerada
            )
        ).remover(id).tarefas();

        repositorioTarefa.excluir(id);
        repositorioTarefa.salvarTodas(uniaoPorId(comAFaixaRenumerada, comAsDuasRenumeradas));
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

    /**
     * Separa a lista em grupos, um por categoria recebida, mais o das sem etiqueta no fim.
     *
     * <p>Os grupos vazios entram: grupo vazio continua sendo área de soltura, e é por ele que
     * se põe o primeiro card numa categoria nova.
     */
    private List<GrupoExibido> agruparPorCategoria(
        List<Tarefa> tarefas, List<Categoria> categorias
    ) {
        Map<Long, List<TarefaComCategoria>> porCategoria = new LinkedHashMap<>();
        Map<Long, Categoria> porId = categorias.stream()
            .collect(Collectors.toMap(Categoria::getId, categoria -> categoria));

        List<TarefaComCategoria> semEtiqueta = new ArrayList<>();
        for (Tarefa tarefa : tarefas) {
            TarefaComCategoria comCategoria = new TarefaComCategoria(
                tarefa, porId.get(tarefa.getCategoriaId())
            );
            if (tarefa.getCategoriaId() == null) {
                semEtiqueta.add(comCategoria);
            } else {
                porCategoria.computeIfAbsent(tarefa.getCategoriaId(), chave -> new ArrayList<>())
                    .add(comCategoria);
            }
        }

        List<GrupoExibido> grupos = new ArrayList<>(categorias.size() + 1);
        for (Categoria categoria : categorias) {
            grupos.add(new GrupoExibido(
                categoria, porCategoria.getOrDefault(categoria.getId(), List.of())
            ));
        }
        grupos.add(new GrupoExibido(null, semEtiqueta));
        return grupos;
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

    /** {@code categoriaId} nulo carrega o grupo das tarefas sem etiqueta. */
    private GrupoDeCategoria carregarGrupo(Long usuarioId, Long categoriaId) {
        return GrupoDeCategoria.de(
            categoriaId, repositorioTarefa.listarPorUsuarioECategoria(usuarioId, categoriaId)
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

    /**
     * A tarefa que acabou de ser posta no grupo, com as posições que o grupo lhe deu.
     *
     * <p>Lança se não estiver lá, e isso é defesa contra erro de programação, não contra
     * entrada do usuário: o card foi inserido duas linhas acima.
     */
    private static Tarefa exigirNoGrupo(GrupoDeCategoria grupo, Long id) {
        return grupo.tarefas().stream()
            .filter(tarefa -> Objects.equals(tarefa.getId(), id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "A tarefa " + id + " não está no grupo onde acabou de ser inserida"));
    }

    /** As da base, trocadas pelas versões que outra operação já mexeu. */
    private static List<Tarefa> comAsVersoesMaisNovas(List<Tarefa> base, List<Tarefa> mexidas) {
        Map<Long, Tarefa> porId = mexidas.stream()
            .collect(Collectors.toMap(Tarefa::getId, tarefa -> tarefa));

        return base.stream()
            .map(tarefa -> porId.getOrDefault(tarefa.getId(), tarefa))
            .toList();
    }

    /** Uma lista só, sem repetir linha: quem aparece nas duas vale pela segunda. */
    private static List<Tarefa> uniaoPorId(List<Tarefa> primeira, List<Tarefa> segunda) {
        Map<Long, Tarefa> porId = new LinkedHashMap<>();
        primeira.forEach(tarefa -> porId.put(tarefa.getId(), tarefa));
        segunda.forEach(tarefa -> porId.put(tarefa.getId(), tarefa));
        return List.copyOf(porId.values());
    }
}
