package com.todonotes.backend.tarefas.dominio;

import java.time.Duration;
import java.time.Instant;

/**
 * As preferências de exibição do dono: se as tarefas concluídas somem sozinhas, e depois de
 * quanto tempo. Uma linha por dono — ou nenhuma.
 *
 * <p><b>Ausência não é erro.</b> Quem nunca abriu a tela não tem linha no banco, e
 * {@link #padrao(Long)} responde por ele. É o que faz o {@code GET /api/preferencias} nunca
 * devolver {@code 404}, e o {@code PUT} ser, na primeira vez, uma inserção.
 */
public class Preferencias {

    /** Sumir sozinha é comportamento que surpreende; quem quiser, liga. */
    public static final boolean OCULTAR_POR_PADRAO = false;

    public static final int DIAS_POR_PADRAO = 7;

    private static final int DIAS_MINIMO = 1;
    private static final int DIAS_MAXIMO = 365;

    private final Long id;
    private final Long usuarioId;
    private final boolean ocultarConcluidasAutomaticamente;
    private final int diasParaOcultar;

    public Preferencias(
        Long id, Long usuarioId, boolean ocultarConcluidasAutomaticamente, int diasParaOcultar
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.ocultarConcluidasAutomaticamente = ocultarConcluidasAutomaticamente;
        this.diasParaOcultar = exigirDiasValidos(diasParaOcultar);
    }

    /**
     * O que vale para quem nunca gravou nada. Sem id: não existe linha, e tentar gravar
     * esta instância direto criaria uma — o que é exatamente o que o {@code PUT} faz.
     */
    public static Preferencias padrao(Long usuarioId) {
        return new Preferencias(null, usuarioId, OCULTAR_POR_PADRAO, DIAS_POR_PADRAO);
    }

    public Preferencias com(boolean ocultarConcluidas, int dias) {
        return new Preferencias(id, usuarioId, ocultarConcluidas, dias);
    }

    /**
     * Diz se esta tarefa já passou do prazo de sumir da lista.
     *
     * <p><b>O prazo é duração, não dias de calendário</b>: sete dias são 168 horas contadas
     * de {@code data_conclusao}. Dias de calendário exigiriam saber o fuso do dono, e
     * ninguém olha a lista no segundo exato em que um card deveria sumir.
     *
     * <p>Só alcança tarefa concluída, e só com a opção ligada. Tarefa pendente nunca some —
     * seria perder trabalho de vista, que é o oposto do que uma lista de tarefas serve.
     */
    public boolean ocultaTarefa(Tarefa tarefa, Instant agora) {
        if (!ocultarConcluidasAutomaticamente || !tarefa.getStatus().concluida()) {
            return false;
        }

        Instant conclusao = tarefa.getDataConclusao();
        if (conclusao == null) {
            return false;
        }

        return !conclusao.isAfter(agora.minus(Duration.ofDays(diasParaOcultar)));
    }

    /** Mesmo intervalo do {@code ck_preferencias_dias}: de 1 a 365. */
    private static int exigirDiasValidos(int dias) {
        if (dias < DIAS_MINIMO || dias > DIAS_MAXIMO) {
            throw new IllegalArgumentException(
                "Os dias para ocultar devem estar entre " + DIAS_MINIMO + " e " + DIAS_MAXIMO);
        }
        return dias;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public boolean isOcultarConcluidasAutomaticamente() {
        return ocultarConcluidasAutomaticamente;
    }

    public int getDiasParaOcultar() {
        return diasParaOcultar;
    }
}
