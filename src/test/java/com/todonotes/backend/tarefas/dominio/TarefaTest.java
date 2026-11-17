package com.todonotes.backend.tarefas.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O que se testa aqui é a amarração entre {@code status} e {@code data_conclusao} — os dois
 * registros do mesmo fato, que a {@code CHECK} da tabela não deixa divergir. O erro que
 * importa não é o banco recusar: é a data da primeira conclusão ser reescrita sem que
 * ninguém peça.
 */
class TarefaTest {

    private static final Long USUARIO = 1L;
    private static final Instant ONTEM = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant HOJE = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void nascePendenteESemDataDeConclusao() {
        Tarefa tarefa = Tarefa.nova("Fechar a V1", null, Prioridade.ALTA, null, USUARIO, 0, ONTEM);

        assertThat(tarefa.getStatus()).isEqualTo(Status.PENDENTE);
        assertThat(tarefa.getDataConclusao()).isNull();
        assertThat(tarefa.getId()).isNull();
        assertThat(tarefa.getCriadaEm()).isEqualTo(ONTEM);
    }

    @Test
    void concluirGravaOInstante() {
        Tarefa concluida = nova().comStatus(Status.CONCLUIDA, HOJE);

        assertThat(concluida.getStatus().concluida()).isTrue();
        assertThat(concluida.getDataConclusao()).isEqualTo(HOJE);
    }

    /**
     * O caso que justifica a saída rápida do {@code comStatus}. Sem ela, um segundo clique
     * em "concluir" — ou um cliente que reenvia a requisição — trocaria a data da conclusão
     * de verdade pela data do reenvio, e o registro de concluídas passaria a mentir sobre
     * quando o trabalho terminou.
     */
    @Test
    void concluirDeNovoNaoReescreveAData() {
        Tarefa concluida = nova().comStatus(Status.CONCLUIDA, ONTEM);

        Tarefa outraVez = concluida.comStatus(Status.CONCLUIDA, HOJE);

        assertThat(outraVez).isSameAs(concluida);
        assertThat(outraVez.getDataConclusao()).isEqualTo(ONTEM);
    }

    @Test
    void reabrirApagaAData() {
        Tarefa concluida = nova().comStatus(Status.CONCLUIDA, ONTEM);

        Tarefa reaberta = concluida.comStatus(Status.PENDENTE, HOJE);

        assertThat(reaberta.getStatus()).isEqualTo(Status.PENDENTE);
        assertThat(reaberta.getDataConclusao()).isNull();
    }

    @Test
    void voltarParaEmAndamentoTambemApagaAData() {
        Tarefa concluida = nova().comStatus(Status.CONCLUIDA, ONTEM);

        Tarefa retomada = concluida.comStatus(Status.EM_ANDAMENTO, HOJE);

        assertThat(retomada.getStatus()).isEqualTo(Status.EM_ANDAMENTO);
        assertThat(retomada.getDataConclusao()).isNull();
    }

    @Test
    void oTituloEAparado() {
        Tarefa tarefa = Tarefa.nova("  Fechar a V1  ", null, Prioridade.ALTA, null, USUARIO, 0, ONTEM);

        assertThat(tarefa.getTitulo()).isEqualTo("Fechar a V1");
    }

    @Test
    void tituloEmBrancoNaoEAceito() {
        assertThatThrownBy(() -> Tarefa.nova("   ", null, Prioridade.ALTA, null, USUARIO, 0, ONTEM))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Tarefa.nova(null, null, Prioridade.ALTA, null, USUARIO, 0, ONTEM))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A {@code CHECK} da tabela recusa descrição em branco, mas aceita nula. Deixar a borda
     * decidir entre {@code ""} e {@code null} faria o banco rejeitar o que a tela considera
     * "sem descrição".
     */
    @Test
    void descricaoEmBrancoViraNula() {
        Tarefa tarefa = Tarefa.nova("Fechar a V1", "   ", Prioridade.ALTA, null, USUARIO, 0, ONTEM);

        assertThat(tarefa.getDescricao()).isNull();
    }

    @Test
    void posicaoNegativaNaoEAceita() {
        assertThatThrownBy(() -> Tarefa.nova("Fechar a V1", null, Prioridade.ALTA, null, USUARIO, -1, ONTEM))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTarefaPrecisaDeDonoEDePrioridade() {
        assertThatThrownBy(() -> Tarefa.nova("Fechar a V1", null, null, null, USUARIO, 0, ONTEM))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> Tarefa.nova("Fechar a V1", null, Prioridade.ALTA, null, null, 0, ONTEM))
            .isInstanceOf(NullPointerException.class);
    }

    /** Prioridade e posição mudam pela faixa; status, por {@code comStatus}. */
    @Test
    void comDadosNaoMexeEmPrioridadePosicaoNemStatus() {
        Tarefa concluida = nova().comStatus(Status.CONCLUIDA, ONTEM);

        Tarefa editada = concluida.comDados("Outro título", "Agora com descrição", 7L);

        assertThat(editada.getTitulo()).isEqualTo("Outro título");
        assertThat(editada.getDescricao()).isEqualTo("Agora com descrição");
        assertThat(editada.getCategoriaId()).isEqualTo(7L);
        assertThat(editada.getPrioridade()).isEqualTo(concluida.getPrioridade());
        assertThat(editada.getPosicao()).isEqualTo(concluida.getPosicao());
        assertThat(editada.getStatus()).isEqualTo(Status.CONCLUIDA);
        assertThat(editada.getDataConclusao()).isEqualTo(ONTEM);
    }

    private static Tarefa nova() {
        return Tarefa.nova("Fechar a V1", null, Prioridade.ALTA, null, USUARIO, 0, ONTEM);
    }
}
