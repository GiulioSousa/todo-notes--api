package com.todonotes.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto inteiro e desliga. Parece não testar nada, e é o teste que mais pega:
 *
 * <ul>
 *   <li><b>O Flyway</b> roda a {@code V1} e grava o histórico dela.</li>
 *   <li><b>O {@code ddl-auto: validate}</b> confronta cada entidade JPA com a tabela — é
 *       aqui que o {@code ENUM} nativo de {@code prioridade} e o {@code SMALLINT} de
 *       {@code dias_para_ocultar} são cobrados.</li>
 *   <li><b>O Spring Data</b> interpreta os nove métodos derivados e compila a {@code @Query}
 *       da categoria. Nome de propriedade errado só aparece neste momento.</li>
 *   <li><b>Toda a injeção</b>, incluindo o {@code @EnableConfigurationProperties} do
 *       {@code SecurityConfig} e o {@code @Bean} do relógio.</li>
 * </ul>
 *
 * <p><b>Precisa do PostgreSQL local no ar</b>, com o {@code todonotes_db} e as duas roles
 * criadas, e do {@code application-local.yml} preenchido. É a convenção do GeFinX — os
 * testes de contexto dele sobem contra o banco de desenvolvimento, sem Testcontainers e sem
 * perfil de teste. O preço é que {@code mvn test} não roda numa máquina sem banco; o que se
 * compra é testar contra o PostgreSQL de verdade, com o {@code ENUM} nativo e a restrição
 * deferida que nenhum banco em memória tem.
 */
@SpringBootTest
class TodoNotesApplicationTests {

    @Test
    void contextoSobe() {
    }
}
