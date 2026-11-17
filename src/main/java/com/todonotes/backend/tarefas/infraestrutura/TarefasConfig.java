package com.todonotes.backend.tarefas.infraestrutura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TarefasConfig {

    /**
     * Um relógio injetado, e não {@code Instant.now()} dentro dos serviços, para que o teste
     * escolha o instante — a regra de ocultar concluídas depois de X dias não tem como ser
     * testada esperando os dias passarem.
     *
     * <p>Em UTC, e sem fuso configurável: o prazo é contado em horas a partir da conclusão,
     * não em dias de calendário (ver a Seção 6). O GeFinX precisa do fuso porque a meta
     * diária dele vira o dia; aqui, saber o fuso do dono não mudaria nenhuma resposta.
     */
    @Bean
    public Clock relogioDasTarefas() {
        return Clock.systemUTC();
    }
}
