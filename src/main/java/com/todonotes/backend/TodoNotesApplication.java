package com.todonotes.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada da aplicação.
 *
 * <p>A exclusão do {@link UserDetailsServiceAutoConfiguration} não é ajuste de estilo. Com
 * o {@code spring-boot-starter-security} no classpath e nenhum {@code UserDetailsService}
 * declarado, o Spring Boot cria um usuário em memória, chamado {@code user}, com uma senha
 * gerada e <b>impressa no log</b> a cada subida. Neste projeto quem autentica é o
 * {@code Autenticar}, contra a tabela {@code usuarios}, e essa conta paralela seria uma
 * segunda porta de entrada que ninguém decidiu abrir.
 *
 * <p>O pacote do import é o do Spring Boot 4 ({@code boot.security.autoconfigure}); no 3 a
 * classe morava em {@code autoconfigure.security.servlet}.
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class TodoNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoNotesApplication.class, args);
    }
}
