package com.todonotes.backend.tarefas.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * O grupo do modo de visualização por categoria.
 *
 * <p><b>Não repete a {@link FaixaDePrioridadeTest}</b>, e isso é de propósito: inserir,
 * remover, mover, renumerar e ajustar a posição fora do intervalo são da
 * {@link GrupoDeTarefas}, que as duas classes compartilham, e testá-las duas vezes faria dois
 * testes guardarem a mesma regra — se ela mudasse, os dois mudariam juntos, e nenhum
 * protegeria nada. O que está aqui é o que só este grupo faz.
 *
 * <p>São três coisas: a chave é o {@code categoriaId} e pode ser nula; a posição lida e
 * escrita é a do grupo de categoria; e <b>a ordem por prioridade não pode ser tocada</b>, que
 * é a promessa dos dois modos.
 */
class GrupoDeCategoriaTest {

    private static final Long CASA = 7L;
    private static final Long TRABALHO = 9L;
    private static final Long USUARIO = 1L;
    private static final Instant CRIADA_EM = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    void oGrupoNasceEmOrdemERenumerado() {
        // Como o banco poderia devolver depois de uma exclusão que não renumerou: 0, 2, 5.
        GrupoDeCategoria grupo = GrupoDeCategoria.de(CASA, List.of(
            tarefa(30L, CASA, 5),
            tarefa(10L, CASA, 0),
            tarefa(20L, CASA, 2)
        ));

        conferir(grupo, 10L, 20L, 30L);
    }

    /**
     * É o que faz de {@code inserir} a única forma de mudar a categoria de um card — a mesma
     * regra da faixa com a prioridade. Quem move entre grupos não precisa, nem consegue,
     * ajustar a etiqueta antes.
     */
    @Test
    void inserirImpoeACategoriaDoGrupo() {
        Tarefa vindaDeOutroGrupo = tarefa(99L, TRABALHO, 0);

        GrupoDeCategoria grupo = grupoCom(CASA, 1L).inserir(vindaDeOutroGrupo, 0);

        conferir(grupo, 99L, 1L);
        assertThat(grupo.tarefas().get(0).getCategoriaId()).isEqualTo(CASA);
        assertThat(vindaDeOutroGrupo.getCategoriaId())
            .as("o card original não é alterado")
            .isEqualTo(TRABALHO);
    }

    /**
     * <b>Arrastar para o grupo das sem etiqueta tira a etiqueta do card.</b> Não é caso
     * especial nem efeito colateral: a categoria é a chave do grupo, e a chave daquele grupo
     * é nula. É como se tira a categoria de uma tarefa sem abrir o formulário.
     */
    @Test
    void inserirNoGrupoSemEtiquetaDesetiquetaOCard() {
        GrupoDeCategoria semEtiqueta = GrupoDeCategoria.vazio(null)
            .inserir(tarefa(1L, CASA, 3), 0);

        assertThat(semEtiqueta.categoriaId()).isNull();
        assertThat(semEtiqueta.tarefas().get(0).getCategoriaId()).isNull();
        conferir(semEtiqueta, 1L);
    }

    /** O grupo das sem etiqueta é um grupo como os outros, e a ordem dele vale igual. */
    @Test
    void oGrupoSemEtiquetaOrdenaComoQualquerOutro() {
        GrupoDeCategoria grupo = GrupoDeCategoria.de(null, List.of(
            tarefa(20L, null, 1),
            tarefa(10L, null, 0)
        )).mover(20L, 0);

        conferir(grupo, 20L, 10L);
    }

    /**
     * <b>A promessa dos dois modos de visualização.</b> Arrastar no grupo de categoria não
     * pode mexer na ordem da faixa de prioridade — se mexesse, reordenar por categoria
     * embaralharia a outra tela.
     *
     * <p>As posições de prioridade aqui estão fora de ordem de propósito: o grupo não é dono
     * delas, e tem de devolvê-las como as recebeu.
     */
    @Test
    void mexerNoGrupoNaoMexeNaOrdemDaFaixa() {
        GrupoDeCategoria grupo = GrupoDeCategoria.de(CASA, List.of(
            tarefaCom(1L, CASA, 0, Prioridade.BAIXA, 4),
            tarefaCom(2L, CASA, 1, Prioridade.ALTA, 2),
            tarefaCom(3L, CASA, 2, Prioridade.MEDIA, 0)
        ));

        GrupoDeCategoria depois = grupo.mover(1L, 2)
            .inserir(tarefaCom(99L, TRABALHO, 0, Prioridade.ALTA, 1), 0);

        conferir(depois, 99L, 2L, 3L, 1L);
        assertThat(depois.tarefas())
            .extracting(Tarefa::getId, Tarefa::getPrioridade, Tarefa::getPosicaoNaPrioridade)
            .containsExactly(
                tuple(99L, Prioridade.ALTA, 1),
                tuple(2L, Prioridade.ALTA, 2),
                tuple(3L, Prioridade.MEDIA, 0),
                tuple(1L, Prioridade.BAIXA, 4)
            );
    }

    /**
     * O que a exclusão de categoria usa: as órfãs entram <b>depois</b> das que já estavam sem
     * etiqueta, na ordem relativa que tinham. Excluir uma categoria não é motivo para
     * reordenar o que ninguém tocou.
     */
    @Test
    void inserirTodasNoFimPreservaAOrdemRelativa() {
        GrupoDeCategoria semEtiqueta = grupoCom(null, 1L, 2L);
        List<Tarefa> orfas = List.of(tarefa(10L, CASA, 0), tarefa(20L, CASA, 1));

        GrupoDeCategoria juntas = semEtiqueta.inserirTodasNoFim(orfas);

        conferir(juntas, 1L, 2L, 10L, 20L);
        assertThat(juntas.tarefas()).allSatisfy(
            tarefa -> assertThat(tarefa.getCategoriaId()).isNull()
        );
    }

    /**
     * Erro de programação, não de quem usa — e a mensagem precisa dizer de qual grupo se
     * trata, inclusive quando a chave é nula.
     */
    @Test
    void moverIdQueNaoEstaNoGrupoNaoEAceito() {
        assertThatThrownBy(() -> grupoCom(null, 1L).mover(99L, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("99")
            .hasMessageContaining("sem categoria");
    }

    /** Confere o invariante inteiro: ordem, posições sem buraco e a categoria do grupo. */
    private static void conferir(GrupoDeCategoria grupo, Long... idsEsperados) {
        assertThat(grupo.tarefas()).extracting(Tarefa::getId).containsExactly(idsEsperados);
        assertThat(grupo.tamanho()).isEqualTo(idsEsperados.length);

        for (int indice = 0; indice < grupo.tamanho(); indice++) {
            Tarefa tarefa = grupo.tarefas().get(indice);

            assertThat(tarefa.getPosicaoNaCategoria())
                .as("posição no grupo da tarefa %d", tarefa.getId())
                .isEqualTo(indice);
            assertThat(tarefa.getCategoriaId())
                .as("categoria da tarefa %d", tarefa.getId())
                .isEqualTo(grupo.categoriaId());
        }
    }

    /** Um grupo já em ordem, com as posições de 0 a n-1. */
    private static GrupoDeCategoria grupoCom(Long categoriaId, Long... ids) {
        List<Tarefa> tarefas = new ArrayList<>(ids.length);
        for (int indice = 0; indice < ids.length; indice++) {
            tarefas.add(tarefa(ids[indice], categoriaId, indice));
        }
        return GrupoDeCategoria.de(categoriaId, tarefas);
    }

    private static Tarefa tarefa(Long id, Long categoriaId, int posicaoNaCategoria) {
        return tarefaCom(id, categoriaId, posicaoNaCategoria, Prioridade.MEDIA, 0);
    }

    private static Tarefa tarefaCom(
        Long id, Long categoriaId, int posicaoNaCategoria,
        Prioridade prioridade, int posicaoNaPrioridade
    ) {
        return new Tarefa(
            id, "Tarefa " + id, null, Status.PENDENTE, prioridade, posicaoNaPrioridade,
            posicaoNaCategoria, categoriaId, USUARIO, CRIADA_EM, null
        );
    }
}
