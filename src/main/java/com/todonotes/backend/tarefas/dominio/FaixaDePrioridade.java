package com.todonotes.backend.tarefas.dominio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * As tarefas de uma prioridade, em ordem — e a regra do arrastar.
 *
 * <p>É o centro do sistema, e a razão de a arquitetura ser em camadas: mover um card é a
 * única coisa neste projeto que tem lógica de verdade, e ela mora aqui, sem Spring, sem JPA
 * e sem HTTP. Os casos de uso carregam a faixa, chamam um método e mandam persistir; nenhum
 * {@code UPDATE} é escrito por eles.
 *
 * <p><b>As posições são sempre {@code 0..n-1}, sem buracos.</b> Toda operação renumera a
 * faixa inteira. A alternativa — posições esparsas, com passo largo — pouparia escritas e
 * cobraria uma rotina de rebalanceamento quando os buracos acabassem; numa faixa de dezenas
 * de linhas, renumerar é mais barato que manter esse código.
 *
 * <p>Escrever a faixa inteira em qualquer ordem só é seguro porque a restrição
 * {@code UNIQUE (usuario_id, prioridade, posicao)} é {@code DEFERRABLE INITIALLY DEFERRED}:
 * o PostgreSQL confere unicidade linha a linha, e trocar duas posições falharia no meio do
 * caminho se a conferência não fosse adiada para o commit.
 *
 * <p>Imutável: cada operação devolve uma faixa nova.
 */
public final class FaixaDePrioridade {

    private final Prioridade prioridade;
    private final List<Tarefa> tarefas;

    private FaixaDePrioridade(Prioridade prioridade, List<Tarefa> tarefas) {
        this.prioridade = prioridade;
        this.tarefas = List.copyOf(tarefas);
    }

    /**
     * Monta a faixa a partir do que veio do banco.
     *
     * <p>Ordena por posição e desempata por id, e <b>renumera</b>. O desempate não deveria
     * ser exercido — a restrição única o impede —, mas uma faixa cuja ordem dependesse da
     * ordem de chegada das linhas seria instável justamente no caso em que algo já está
     * errado, e aí a tela mudaria sozinha a cada carregamento.
     */
    public static FaixaDePrioridade de(Prioridade prioridade, List<Tarefa> tarefas) {
        Objects.requireNonNull(prioridade, "A faixa precisa de uma prioridade");

        List<Tarefa> ordenadas = tarefas.stream()
            .sorted(Comparator.comparingInt(Tarefa::getPosicao)
                .thenComparing(Tarefa::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        return new FaixaDePrioridade(prioridade, renumerar(prioridade, ordenadas));
    }

    public static FaixaDePrioridade vazia(Prioridade prioridade) {
        return new FaixaDePrioridade(prioridade, List.of());
    }

    /**
     * Insere na posição pedida e renumera.
     *
     * <p>A posição é <b>ajustada</b> ao intervalo válido, nunca recusada: o cliente pode
     * estar um passo atrás da realidade — um card excluído em outra aba, por exemplo —, e um
     * arrastar que falha por isso é pior que um arrastar que acomoda. Posição além do fim
     * põe no fim; negativa, no começo.
     *
     * <p>A tarefa recebe a prioridade <b>desta</b> faixa, o que faz de inserir aqui a única
     * forma de mudar a prioridade de um card: quem chama não precisa — nem consegue —
     * ajustá-la antes.
     */
    public FaixaDePrioridade inserir(Tarefa tarefa, int posicao) {
        List<Tarefa> nova = new ArrayList<>(tarefas);
        nova.add(ajustarAoIntervalo(posicao, nova.size()), tarefa);
        return new FaixaDePrioridade(prioridade, renumerar(prioridade, nova));
    }

    /** Insere no fim: é onde a tarefa criada entra, para não passar na frente do que já foi ordenado. */
    public FaixaDePrioridade inserirNoFim(Tarefa tarefa) {
        return inserir(tarefa, tarefas.size());
    }

    public FaixaDePrioridade remover(long tarefaId) {
        List<Tarefa> nova = tarefas.stream()
            .filter(tarefa -> !Objects.equals(tarefa.getId(), tarefaId))
            .toList();

        return new FaixaDePrioridade(prioridade, renumerar(prioridade, nova));
    }

    /**
     * Move um card dentro da própria faixa.
     *
     * <p><b>A posição é o índice na lista final</b>, depois de o card ser tirado de onde
     * estava — a mesma convenção do {@code currentIndex} do {@code cdk/drag-drop}. Tratá-la
     * como índice na lista original erraria por um em todo movimento para baixo, e o card
     * pararia sempre uma casa antes do lugar onde foi solto.
     */
    public FaixaDePrioridade mover(long tarefaId, int novaPosicao) {
        Tarefa alvo = tarefas.stream()
            .filter(tarefa -> Objects.equals(tarefa.getId(), tarefaId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "A tarefa " + tarefaId + " não está na faixa " + prioridade));

        return remover(tarefaId).inserir(alvo, novaPosicao);
    }

    public boolean contem(long tarefaId) {
        return tarefas.stream().anyMatch(tarefa -> Objects.equals(tarefa.getId(), tarefaId));
    }

    /** As tarefas em ordem, já com as posições que devem ir para o banco. */
    public List<Tarefa> tarefas() {
        return tarefas;
    }

    public Prioridade prioridade() {
        return prioridade;
    }

    public int tamanho() {
        return tarefas.size();
    }

    private static List<Tarefa> renumerar(Prioridade prioridade, List<Tarefa> tarefas) {
        List<Tarefa> resultado = new ArrayList<>(tarefas.size());
        for (int indice = 0; indice < tarefas.size(); indice++) {
            resultado.add(tarefas.get(indice).comLugar(prioridade, indice));
        }
        return resultado;
    }

    private static int ajustarAoIntervalo(int posicao, int tamanho) {
        return Math.max(0, Math.min(posicao, tamanho));
    }
}
