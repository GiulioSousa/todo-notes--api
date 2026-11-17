package com.todonotes.backend.usuarios.dominio.excecoes;

/**
 * A mensagem é a mesma para conta inexistente e para senha errada, de propósito:
 * distinguir os dois casos diria a quem sonda quais contas existem no sistema. E sem rota
 * de cadastro, sondar o login é o único jeito que resta de descobrir isso.
 *
 * <p>Quem distingue é o log da aplicação, que só quem opera o sistema lê.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos");
    }
}
