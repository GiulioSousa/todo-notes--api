package com.todonotes.backend.tarefas.dominio;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A regra de ocultar concluídas é contada em <b>duração</b>, não em dias de calendário: sete
 * dias são 168 horas a partir da conclusão. É o que dispensa saber o fuso do dono — e é
 * exatamente o tipo de regra que não se testa com o relógio do sistema, porque esperar os
 * sete dias não é teste.
 */
class PreferenciasTest {

    private static final Long USUARIO = 1L;
    private static final Instant AGORA = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void oPadraoNaoOcultaNadaEVemSemId() {
        Preferencias padrao = Preferencias.padrao(USUARIO);

        assertThat(padrao.getId()).as("sem linha no banco, nada a atualizar").isNull();
        assertThat(padrao.isOcultarConcluidasAutomaticamente()).isFalse();
        assertThat(padrao.getDiasParaOcultar()).isEqualTo(Preferencias.DIAS_POR_PADRAO);
        assertThat(padrao.ocultaTarefa(concluidaHa(Duration.ofDays(365)), AGORA)).isFalse();
    }

    @Test
    void comAOpcaoDesligadaNadaEOcultado() {
        Preferencias preferencias = Preferencias.padrao(USUARIO).com(false, 1);

        assertThat(preferencias.ocultaTarefa(concluidaHa(Duration.ofDays(30)), AGORA)).isFalse();
    }

    @Test
    void ocultaDepoisDoPrazo() {
        Preferencias preferencias = ocultandoEm(7);

        assertThat(preferencias.ocultaTarefa(concluidaHa(Duration.ofDays(8)), AGORA)).isTrue();
    }

    @Test
    void naoOcultaAntesDoPrazo() {
        Preferencias preferencias = ocultandoEm(7);

        assertThat(preferencias.ocultaTarefa(concluidaHa(Duration.ofDays(7).minusSeconds(1)), AGORA))
            .isFalse();
    }

    /**
     * No instante exato do prazo, oculta. A escolha entre ocultar e esperar um segundo é
     * arbitrária e não afeta ninguém — ninguém olha a lista no segundo em que um card
     * deveria sumir —, mas fica fixada aqui para não mudar sozinha numa refatoração.
     */
    @Test
    void noPrazoExatoJaOculta() {
        Preferencias preferencias = ocultandoEm(7);

        assertThat(preferencias.ocultaTarefa(concluidaHa(Duration.ofDays(7)), AGORA)).isTrue();
    }

    /**
     * Tarefa pendente nunca some: seria perder trabalho de vista, que é o oposto do que uma
     * lista de tarefas serve.
     */
    @Test
    void tarefaNaoConcluidaNuncaEOcultada() {
        Preferencias preferencias = ocultandoEm(1);
        Tarefa pendente = Tarefa.nova(
            "Fechar a V1", null, Prioridade.ALTA, null, USUARIO, 0, AGORA.minus(Duration.ofDays(400))
        );

        assertThat(preferencias.ocultaTarefa(pendente, AGORA)).isFalse();
        assertThat(preferencias.ocultaTarefa(pendente.comStatus(Status.EM_ANDAMENTO, AGORA), AGORA))
            .isFalse();
    }

    @Test
    void diasForaDoIntervaloNaoSaoAceitos() {
        Preferencias preferencias = Preferencias.padrao(USUARIO);

        assertThatThrownBy(() -> preferencias.com(true, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preferencias.com(true, 366))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(preferencias.com(true, 1).getDiasParaOcultar()).isEqualTo(1);
        assertThat(preferencias.com(true, 365).getDiasParaOcultar()).isEqualTo(365);
    }

    /** É o id que faz o {@code PUT} ser atualização em vez de uma segunda linha. */
    @Test
    void comPreservaOIdEODono() {
        Preferencias gravada = new Preferencias(5L, USUARIO, false, 7);

        Preferencias alterada = gravada.com(true, 30);

        assertThat(alterada.getId()).isEqualTo(5L);
        assertThat(alterada.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(alterada.isOcultarConcluidasAutomaticamente()).isTrue();
        assertThat(alterada.getDiasParaOcultar()).isEqualTo(30);
    }

    private static Preferencias ocultandoEm(int dias) {
        return Preferencias.padrao(USUARIO).com(true, dias);
    }

    private static Tarefa concluidaHa(Duration tempo) {
        return Tarefa.nova("Fechar a V1", null, Prioridade.ALTA, null, USUARIO, 0, AGORA.minus(tempo))
            .comStatus(Status.CONCLUIDA, AGORA.minus(tempo));
    }
}
