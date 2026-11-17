package com.todonotes.backend.tarefas.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A regra do arrastar é a única lógica de verdade deste projeto, e estes testes existem
 * porque ela erra <b>em silêncio</b>: um card uma casa fora do lugar não lança exceção, não
 * quebra restrição nenhuma e só aparece como "arrastei e ele foi para outro lugar".
 *
 * <p>Toda verificação passa por {@link #conferir}, que confere o invariante inteiro da faixa
 * — ordem, posições {@code 0..n-1} sem buracos, e a prioridade da faixa em todo card —, e
 * não apenas o que cada caso tem de particular.
 */
class FaixaDePrioridadeTest {

    private static final Prioridade PRIORIDADE = Prioridade.MEDIA;
    private static final Long USUARIO = 1L;
    private static final Instant CRIADA_EM = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void aFaixaNasceEmOrdemERenumerada() {
        // Como o banco poderia devolver depois de uma exclusão que não renumerou: 0, 2, 5.
        FaixaDePrioridade faixa = FaixaDePrioridade.de(PRIORIDADE, List.of(
            tarefa(30L, PRIORIDADE, 5),
            tarefa(10L, PRIORIDADE, 0),
            tarefa(20L, PRIORIDADE, 2)
        ));

        conferir(faixa, 10L, 20L, 30L);
    }

    /**
     * A restrição única impede isso no banco. O desempate existe para que a faixa não mude
     * de ordem a cada carregamento justamente quando algo já está errado — uma lista que se
     * reorganiza sozinha é pior que uma lista errada e estável.
     */
    @Test
    void posicoesRepetidasDesempatamPeloId() {
        FaixaDePrioridade faixa = FaixaDePrioridade.de(PRIORIDADE, List.of(
            tarefa(20L, PRIORIDADE, 0),
            tarefa(10L, PRIORIDADE, 0)
        ));

        conferir(faixa, 10L, 20L);
    }

    @Test
    void inserirNoMeioEmpurraOsSeguintes() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L).inserir(tarefa(99L, PRIORIDADE, 0), 1);

        conferir(faixa, 1L, 99L, 2L, 3L);
    }

    /**
     * O cliente pode estar um passo atrás da realidade — um card excluído em outra aba, por
     * exemplo. Um arrastar que falha por isso é pior que um arrastar que acomoda.
     */
    @Test
    void posicaoAlemDoFimEAjustadaParaOFim() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L).inserir(tarefa(99L, PRIORIDADE, 0), 47);

        conferir(faixa, 1L, 2L, 99L);
    }

    @Test
    void posicaoNegativaVaiParaOComeco() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L).inserir(tarefa(99L, PRIORIDADE, 0), -3);

        conferir(faixa, 99L, 1L, 2L);
    }

    /**
     * É o que faz de {@code inserir} a única forma de mudar a prioridade de um card: quem
     * move entre faixas não precisa — nem consegue — ajustá-la antes.
     */
    @Test
    void inserirImpoeAPrioridadeDaFaixa() {
        Tarefa vindaDeOutraFaixa = tarefa(99L, Prioridade.ALTA, 0);

        FaixaDePrioridade faixa = faixaCom(1L).inserir(vindaDeOutraFaixa, 0);

        conferir(faixa, 99L, 1L);
        assertThat(vindaDeOutraFaixa.getPrioridade())
            .as("o card original não é alterado")
            .isEqualTo(Prioridade.ALTA);
    }

    /**
     * A renumeração devolve a própria instância para quem já está no lugar certo. Num
     * arrastar de uma casa, só dois cards ganham objeto novo — é o que permitiria gravar
     * apenas o que mudou, se a faixa crescer a ponto de importar.
     */
    @Test
    void renumerarPreservaAsInstanciasDeQuemNaoMudouDeLugar() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L);
        Tarefa primeira = faixa.tarefas().get(0);
        Tarefa segunda = faixa.tarefas().get(1);

        FaixaDePrioridade comNova = faixa.inserirNoFim(tarefa(99L, PRIORIDADE, 0));

        assertThat(comNova.tarefas().get(0)).isSameAs(primeira);
        assertThat(comNova.tarefas().get(1)).isSameAs(segunda);
        conferir(comNova, 1L, 2L, 99L);
    }

    @Test
    void removerFechaOBuraco() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L).remover(2L);

        conferir(faixa, 1L, 3L);
    }

    @Test
    void removerIdInexistenteNaoMudaNada() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L).remover(99L);

        conferir(faixa, 1L, 2L);
    }

    /**
     * <b>O teste decisivo.</b> A posição é o índice na lista <b>final</b>, depois de o card
     * ser tirado de onde estava — a mesma convenção do {@code currentIndex} do
     * {@code cdk/drag-drop}. Tratada como índice na lista original, daria
     * {@code 2, 1, 3, 4}: o card pararia uma casa antes de onde foi solto, em todo
     * movimento para baixo.
     */
    @Test
    void moverParaBaixoUsaOIndiceNaListaFinal() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L, 4L).mover(1L, 2);

        conferir(faixa, 2L, 3L, 1L, 4L);
    }

    @Test
    void moverParaCima() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L, 4L).mover(4L, 1);

        conferir(faixa, 1L, 4L, 2L, 3L);
    }

    @Test
    void moverParaAMesmaPosicaoNaoMudaAOrdem() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L).mover(2L, 1);

        conferir(faixa, 1L, 2L, 3L);
    }

    @Test
    void moverAlemDoFimVaiParaOFim() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L, 3L).mover(1L, 10);

        conferir(faixa, 2L, 3L, 1L);
    }

    /**
     * Erro de programação, não de quem usa: o caso de uso carrega a faixa da tarefa antes de
     * mover, e a resposta para id desconhecido é o {@code 404} que ele dá antes de chegar
     * aqui.
     */
    @Test
    void moverIdQueNaoEstaNaFaixaNaoEAceito() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L);

        assertThatThrownBy(() -> faixa.mover(99L, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("99")
            .hasMessageContaining(PRIORIDADE.name());
    }

    @Test
    void aFaixaOriginalNaoMuda() {
        FaixaDePrioridade original = faixaCom(1L, 2L);

        original.inserir(tarefa(99L, PRIORIDADE, 0), 0);
        original.remover(1L);
        original.mover(1L, 1);

        conferir(original, 1L, 2L);
    }

    @Test
    void aListaDevolvidaNaoAceitaAlteracao() {
        List<Tarefa> tarefas = faixaCom(1L).tarefas();

        assertThatThrownBy(() -> tarefas.add(tarefa(99L, PRIORIDADE, 1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void faixaVaziaAceitaOPrimeiroCard() {
        FaixaDePrioridade vazia = FaixaDePrioridade.vazia(PRIORIDADE);

        assertThat(vazia.tamanho()).isZero();
        conferir(vazia.inserir(tarefa(1L, PRIORIDADE, 0), 7), 1L);
    }

    @Test
    void contemResponderSobreOQueEstaNaFaixa() {
        FaixaDePrioridade faixa = faixaCom(1L, 2L);

        assertThat(faixa.contem(1L)).isTrue();
        assertThat(faixa.contem(99L)).isFalse();
    }

    /** Confere o invariante inteiro: ordem, posições sem buraco e a prioridade da faixa. */
    private static void conferir(FaixaDePrioridade faixa, Long... idsEsperados) {
        assertThat(faixa.tarefas()).extracting(Tarefa::getId).containsExactly(idsEsperados);
        assertThat(faixa.tamanho()).isEqualTo(idsEsperados.length);

        for (int indice = 0; indice < faixa.tamanho(); indice++) {
            Tarefa tarefa = faixa.tarefas().get(indice);

            assertThat(tarefa.getPosicao())
                .as("posição da tarefa %d", tarefa.getId())
                .isEqualTo(indice);
            assertThat(tarefa.getPrioridade())
                .as("prioridade da tarefa %d", tarefa.getId())
                .isEqualTo(faixa.prioridade());
        }
    }

    /** Uma faixa já em ordem, com as posições de 0 a n-1. */
    private static FaixaDePrioridade faixaCom(Long... ids) {
        List<Tarefa> tarefas = new ArrayList<>(ids.length);
        for (int indice = 0; indice < ids.length; indice++) {
            tarefas.add(tarefa(ids[indice], PRIORIDADE, indice));
        }
        return FaixaDePrioridade.de(PRIORIDADE, tarefas);
    }

    private static Tarefa tarefa(Long id, Prioridade prioridade, int posicao) {
        return new Tarefa(
            id, "Tarefa " + id, null, Status.PENDENTE, prioridade, posicao,
            null, USUARIO, CRIADA_EM, null
        );
    }
}
