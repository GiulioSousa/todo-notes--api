package com.todonotes.backend.tarefas.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Uma tarefa: o card da lista.
 *
 * <p><b>Imutável.</b> Cada transição devolve uma instância nova, e quem chamou decide o que
 * persistir. Não há objeto gerenciado pelo Hibernate neste lado da fronteira — o adaptador
 * traduz —, então não existe dirty checking para confiar, e tornar a mudança explícita no
 * ponto da chamada é o que resta de honesto.
 *
 * <p>O dono é {@code usuarioId}, um número, e não uma referência a {@code Usuario}: é o que
 * mantém {@code tarefas} e {@code usuarios} sem acoplamento.
 *
 * <p>A categoria também é só o id. O nome que aparece no card é resolvido na borda, ao
 * montar o DTO — carregar a categoria inteira aqui faria toda listagem arrastar texto que o
 * domínio nunca lê.
 *
 * <p><b>Duas posições, uma por agrupamento.</b> A tela mostra as tarefas agrupadas por
 * prioridade ou por categoria, e cada modo guarda a sua própria ordem: arrastar num deles
 * não mexe no outro. Por isso não existe {@code posicao} nesta classe — o nome não diria de
 * qual das duas ordens se trata. Quem as escreve são os {@link GrupoDeTarefas}, e só eles.
 */
public class Tarefa {

    private final Long id;
    private final String titulo;
    private final String descricao;
    private final Status status;
    private final Prioridade prioridade;
    private final int posicaoNaPrioridade;
    private final int posicaoNaCategoria;
    private final Long categoriaId;
    private final Long usuarioId;
    private final Instant criadaEm;
    private final Instant dataConclusao;

    public Tarefa(
        Long id, String titulo, String descricao, Status status, Prioridade prioridade,
        int posicaoNaPrioridade, int posicaoNaCategoria, Long categoriaId, Long usuarioId,
        Instant criadaEm, Instant dataConclusao
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.status = status;
        this.prioridade = prioridade;
        this.posicaoNaPrioridade = posicaoNaPrioridade;
        this.posicaoNaCategoria = posicaoNaCategoria;
        this.categoriaId = categoriaId;
        this.usuarioId = usuarioId;
        this.criadaEm = criadaEm;
        this.dataConclusao = dataConclusao;
    }

    /**
     * Tarefa recém-criada: nasce {@link Status#PENDENTE}, sem conclusão e sem id.
     *
     * <p>As duas posições vêm de fora, e são <b>o fim de cada um dos dois grupos a que a
     * tarefa passa a pertencer</b>: o da prioridade escolhida e o da categoria escolhida —
     * que pode ser o grupo das sem etiqueta. Quem as calcula é o caso de uso, por contagem,
     * porque as posições são {@code 0..n-1} sem buracos e a contagem <b>é</b> o fim do
     * grupo; carregar os dois grupos inteiros só para descobrir isso custaria duas
     * listagens e reescreveria todo card que não mudou de lugar.
     */
    public static Tarefa nova(
        String titulo, String descricao, Prioridade prioridade, Long categoriaId, Long usuarioId,
        int posicaoNaPrioridade, int posicaoNaCategoria, Instant agora
    ) {
        return new Tarefa(
            null, exigirTitulo(titulo), normalizarDescricao(descricao), Status.PENDENTE,
            Objects.requireNonNull(prioridade, "A prioridade é obrigatória"),
            exigirPosicaoValida(posicaoNaPrioridade), exigirPosicaoValida(posicaoNaCategoria),
            categoriaId,
            Objects.requireNonNull(usuarioId, "A tarefa precisa de um dono"),
            agora, null
        );
    }

    /**
     * Muda o status, e com ele a data de conclusão — que é a mesma informação dita duas
     * vezes, e por isso nunca é parâmetro: quem a define é este método, com o relógio do
     * servidor. A {@code CHECK} da tabela guarda a mesma regra do lado do banco.
     *
     * <p>Repetir o status atual devolve a própria instância, e isso importa no caso de
     * {@link Status#CONCLUIDA}: concluir duas vezes <b>não</b> reescreve a data da primeira
     * conclusão, que é o que o registro de concluídas existe para guardar.
     */
    public Tarefa comStatus(Status novoStatus, Instant agora) {
        Objects.requireNonNull(novoStatus, "O status é obrigatório");
        if (novoStatus == this.status) {
            return this;
        }

        Instant novaDataConclusao = novoStatus.concluida() ? agora : null;
        return new Tarefa(
            id, titulo, descricao, novoStatus, prioridade, posicaoNaPrioridade,
            posicaoNaCategoria, categoriaId, usuarioId, criadaEm, novaDataConclusao
        );
    }

    /**
     * Põe a tarefa num lugar da lista agrupada por prioridade — e lugar é o par
     * {@code (prioridade, posicaoNaPrioridade)}.
     *
     * <p>Devolve a própria instância quando nada muda. A {@link FaixaDePrioridade} renumera
     * a faixa inteira a cada movimento, e na prática a maioria dos cards fica onde estava;
     * sem esta saída, cada arrastar produziria objetos novos para todos eles.
     *
     * <p>É de uso da faixa, não dos casos de uso: mover um card sem passar por ela deixaria
     * duas tarefas na mesma posição.
     */
    Tarefa comLugar(Prioridade novaPrioridade, int novaPosicao) {
        if (novaPrioridade == this.prioridade && novaPosicao == this.posicaoNaPrioridade) {
            return this;
        }

        return new Tarefa(
            id, titulo, descricao, status, novaPrioridade, exigirPosicaoValida(novaPosicao),
            posicaoNaCategoria, categoriaId, usuarioId, criadaEm, dataConclusao
        );
    }

    /**
     * O mesmo, na lista agrupada por categoria: o par
     * {@code (categoriaId, posicaoNaCategoria)}.
     *
     * <p><b>Entrar num grupo é o que muda a categoria do card</b>, do mesmo jeito que entrar
     * numa faixa é o que muda a prioridade. {@code null} é o grupo das tarefas sem etiqueta,
     * e arrastar um card para lá tira a etiqueta dele — não é caso especial, é o grupo cuja
     * chave é nula. A unicidade no banco enxerga esse grupo como qualquer outro, porque é
     * {@code NULLS NOT DISTINCT}.
     */
    Tarefa comLugarNaCategoria(Long novaCategoriaId, int novaPosicao) {
        if (Objects.equals(novaCategoriaId, this.categoriaId)
            && novaPosicao == this.posicaoNaCategoria) {
            return this;
        }

        return new Tarefa(
            id, titulo, descricao, status, prioridade, posicaoNaPrioridade,
            exigirPosicaoValida(novaPosicao), novaCategoriaId, usuarioId, criadaEm, dataConclusao
        );
    }

    /**
     * Edita o que o formulário edita de verdade: título e descrição.
     *
     * <p>Prioridade, categoria e as duas posições ficam de fora, e por um motivo só — as
     * três são chaves ou ordens de grupo, e quem as escreve são os {@link GrupoDeTarefas}.
     * Status muda por {@link #comStatus}. Deixar a categoria aqui faria o card trocar de
     * grupo sem ganhar lugar nele, e duas tarefas acabariam na mesma posição do grupo de
     * destino.
     */
    public Tarefa comDados(String novoTitulo, String novaDescricao) {
        return new Tarefa(
            id, exigirTitulo(novoTitulo), normalizarDescricao(novaDescricao), status, prioridade,
            posicaoNaPrioridade, posicaoNaCategoria, categoriaId, usuarioId, criadaEm,
            dataConclusao
        );
    }

    private static String exigirTitulo(String titulo) {
        String limpo = titulo == null ? "" : titulo.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("O título é obrigatório");
        }
        return limpo;
    }

    /**
     * Descrição vazia vira nula. A {@code CHECK} da tabela recusa texto em branco, e deixar
     * a borda decidir entre {@code null} e {@code ""} faria o banco rejeitar o que a tela
     * considera "sem descrição".
     */
    private static String normalizarDescricao(String descricao) {
        if (descricao == null) {
            return null;
        }
        String limpa = descricao.trim();
        return limpa.isEmpty() ? null : limpa;
    }

    private static int exigirPosicaoValida(int posicao) {
        if (posicao < 0) {
            throw new IllegalArgumentException("A posição não pode ser negativa");
        }
        return posicao;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Status getStatus() {
        return status;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public int getPosicaoNaPrioridade() {
        return posicaoNaPrioridade;
    }

    public int getPosicaoNaCategoria() {
        return posicaoNaCategoria;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getDataConclusao() {
        return dataConclusao;
    }
}
