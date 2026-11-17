package com.todonotes.backend.compartilhado.seguranca;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * O id do dono da requisição, posto no contexto pelo {@link FiltroAutenticacaoJwt}.
 *
 * <p>É daqui que sai o filtro de toda consulta do sistema. Nenhum caso de uso recebe o id
 * do dono por parâmetro vindo da borda — se recebesse, trocar um número na URL daria acesso
 * aos dados de outra conta.
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    public static Long obterId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
