package com.todonotes.backend.usuarios.interfaces.web.dto;

import java.time.Instant;

/**
 * @param expiraEm instante em que o token deixa de valer, em UTC. Vai para o cliente para
 *                 que a tela saiba reautenticar antes de uma requisição falhar, em vez de
 *                 descobrir pelo primeiro `401`.
 */
public record RespostaAutenticacao(String token, String usuario, Instant expiraEm) {
}
