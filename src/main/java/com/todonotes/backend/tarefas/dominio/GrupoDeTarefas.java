package com.todonotes.backend.tarefas.dominio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Um grupo de tarefas em ordem — e a regra do arrastar, escrita uma vez para os dois modos
 * de visualização.
 *
 * <p>É o centro do sistema, e a razão de a arquitetura ser em camadas: mover um card é a
 * única coisa neste projeto que tem lógica de verdade, e ela mora aqui, sem Spring, sem JPA
 * e sem HTTP. Os casos de uso carregam o grupo, chamam um método e mandam persistir; nenhum
 * {@code UPDATE} é escrito por eles.
 *
 * <p><b>Dois agrupamentos, uma regra.</b> A lista pode ser agrupada por prioridade
 * ({@link FaixaDePrioridade}) ou por categoria ({@link GrupoDeCategoria}), e cada modo tem a
 * sua própria ordem — arrastar num deles não mexe no outro. O que muda entre os dois é
 * apenas qual chave o grupo impõe ao card que entra e qual das duas posições da
 * {@link Tarefa} ele lê e escreve; inserir, remover, mover e renumerar são idênticos, e
 * copiá-los seria manter a mesma regra em dois lugares, que divergiriam no primeiro ajuste.
 *
 * <p><b>As posições são sempre {@code 0..n-1}, sem buracos.</b> Toda operação renumera o
 * grupo inteiro. A alternativa — posições esparsas, com passo largo — pouparia escritas e
 * cobraria uma rotina de rebalanceamento quando os buracos acabassem; num grupo de dezenas
 * de linhas, renumerar é mais barato que manter esse código.
 *
 * <p>Escrever o grupo inteiro em qualquer ordem só é seguro porque as restrições
 * {@code uq_tarefas_ordem_na_prioridade} e {@code uq_tarefas_ordem_na_categoria} são
 * {@code DEFERRABLE INITIALLY DEFERRED}: o PostgreSQL confere unicidade linha a linha, e
 * trocar duas posições falharia no meio do caminho se a conferência não fosse adiada para o
 * commit.
 *
 * <p>Imutável: cada operação devolve um grupo novo. O parâmetro {@code G} existe para que
 * esse grupo novo tenha o tipo do grupo de origem — {@code faixa.mover(...)} devolve uma
 * {@code FaixaDePrioridade}, e não um grupo qualquer que o chamador tivesse de converter.
 */
public abstract sealed class GrupoDeTarefas<G extends GrupoDeTarefas<G>>
    permits FaixaDePrioridade, GrupoDeCategoria {

    private final List<Tarefa> tarefas;

    /**
     * Recebe a lista como ela vier. Quem a põe em ordem é {@link #normalizado()}, e é por lá
     * que passam as fábricas das filhas — este construtor não pode fazê-lo sozinho, porque
     * ordenar e renumerar dependem de métodos que só a filha sabe responder.
     */
    protected GrupoDeTarefas(List<Tarefa> tarefas) {
        this.tarefas = List.copyOf(tarefas);
    }

    /** A posição da tarefa <b>neste</b> agrupamento, das duas que ela guarda. */
    protected abstract int posicaoNoGrupo(Tarefa tarefa);

    /**
     * Põe a tarefa no lugar: a chave deste grupo e a posição.
     *
     * <p>É por aqui que entrar num grupo muda a prioridade ou a categoria do card, e é o que
     * faz de {@link #inserir} a única forma de fazê-lo.
     */
    protected abstract Tarefa comLugarNesteGrupo(Tarefa tarefa, int posicao);

    /** O mesmo grupo com outra lista, já ordenada e renumerada. */
    protected abstract G comTarefas(List<Tarefa> tarefas);

    /**
     * O mesmo grupo em ordem, numerado de 0 — o estado em que toda operação o encontra.
     *
     * <p>É o que as fábricas das filhas chamam depois de construir: o que vem do banco já
     * deveria estar nesse estado, e normalizar na entrada é o que garante que esteja, mesmo
     * que a tabela tenha um buraco de uma versão anterior do código.
     */
    protected final G normalizado() {
        return comTarefas(renumerar(ordenadasPorPosicao(tarefas)));
    }

    /**
     * Insere na posição pedida e renumera.
     *
     * <p>A posição é <b>ajustada</b> ao intervalo válido, nunca recusada: o cliente pode
     * estar um passo atrás da realidade — um card excluído em outra aba, por exemplo —, e um
     * arrastar que falha por isso é pior que um arrastar que acomoda. Posição além do fim
     * põe no fim; negativa, no começo.
     *
     * <p>A tarefa recebe a chave <b>deste</b> grupo, o que faz de inserir aqui a única forma
     * de mudar a prioridade ou a categoria de um card: quem chama não precisa — nem consegue
     * — ajustá-la antes.
     */
    public final G inserir(Tarefa tarefa, int posicao) {
        List<Tarefa> nova = new ArrayList<>(tarefas);
        nova.add(ajustarAoIntervalo(posicao, nova.size()), tarefa);
        return comTarefas(renumerar(nova));
    }

    /**
     * Insere no fim: é onde a tarefa criada entra, e onde para o card que troca de grupo pelo
     * formulário — nenhum dos dois passa na frente do que já foi ordenado.
     */
    public final G inserirNoFim(Tarefa tarefa) {
        return inserir(tarefa, tarefas.size());
    }

    /** Insere todas no fim, na ordem em que vierem. */
    public final G inserirTodasNoFim(List<Tarefa> novas) {
        List<Tarefa> juntas = new ArrayList<>(tarefas);
        juntas.addAll(novas);
        return comTarefas(renumerar(juntas));
    }

    public final G remover(long tarefaId) {
        List<Tarefa> nova = tarefas.stream()
            .filter(tarefa -> !Objects.equals(tarefa.getId(), tarefaId))
            .toList();

        return comTarefas(renumerar(nova));
    }

    /**
     * Move um card dentro do próprio grupo.
     *
     * <p><b>A posição é o índice na lista final</b>, depois de o card ser tirado de onde
     * estava — a mesma convenção do {@code currentIndex} do {@code cdk/drag-drop}. Tratá-la
     * como índice na lista original erraria por um em todo movimento para baixo, e o card
     * pararia sempre uma casa antes do lugar onde foi solto.
     */
    public final G mover(long tarefaId, int novaPosicao) {
        Tarefa alvo = tarefas.stream()
            .filter(tarefa -> Objects.equals(tarefa.getId(), tarefaId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "A tarefa " + tarefaId + " não está no grupo " + chave()));

        return remover(tarefaId).inserir(alvo, novaPosicao);
    }

    public final boolean contem(long tarefaId) {
        return tarefas.stream().anyMatch(tarefa -> Objects.equals(tarefa.getId(), tarefaId));
    }

    /** As tarefas em ordem, já com as posições que devem ir para o banco. */
    public final List<Tarefa> tarefas() {
        return tarefas;
    }

    public final int tamanho() {
        return tarefas.size();
    }

    /** A chave do grupo, para mensagem de erro: a prioridade ou o id da categoria. */
    protected abstract Object chave();

    /**
     * Ordena pela posição deste agrupamento e desempata por id.
     *
     * <p>O desempate não deveria ser exercido — a restrição única o impede —, mas um grupo
     * cuja ordem dependesse da ordem de chegada das linhas seria instável justamente no caso
     * em que algo já está errado, e aí a tela mudaria sozinha a cada carregamento.
     */
    protected final List<Tarefa> ordenadasPorPosicao(List<Tarefa> tarefas) {
        return tarefas.stream()
            .sorted(Comparator.comparingInt(this::posicaoNoGrupo)
                .thenComparing(Tarefa::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    protected final List<Tarefa> renumerar(List<Tarefa> tarefas) {
        List<Tarefa> resultado = new ArrayList<>(tarefas.size());
        for (int indice = 0; indice < tarefas.size(); indice++) {
            resultado.add(comLugarNesteGrupo(tarefas.get(indice), indice));
        }
        return resultado;
    }

    private static int ajustarAoIntervalo(int posicao, int tamanho) {
        return Math.max(0, Math.min(posicao, tamanho));
    }
}
