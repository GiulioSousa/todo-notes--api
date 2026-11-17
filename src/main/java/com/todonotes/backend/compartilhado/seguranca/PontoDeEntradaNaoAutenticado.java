package com.todonotes.backend.compartilhado.seguranca;

import com.todonotes.backend.compartilhado.excecoes.ErroResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Responde `401` no mesmo formato dos demais erros da API.
 *
 * <p>Sem isto, a recusa sairia por {@code sendError}, com {@code Content-Length: 0} — um
 * `401` mudo, e fora do formato único, justamente na resposta que o frontend mais lê, já que
 * é ela que decide encerrar a sessão.
 *
 * <p>O corpo é escrito com {@code setStatus}, e não {@code sendError}, de propósito: o
 * segundo entrega a resposta à página de erro do contêiner, que descartaria o que fosse
 * escrito aqui.
 *
 * <p>A mensagem não distingue token ausente de expirado. A diferença não muda o que o
 * cliente faz — reautenticar — e enunciá-la contaria a quem sonda em que estado está o token
 * que ele tem em mãos.
 */
@Component
public class PontoDeEntradaNaoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public PontoDeEntradaNaoAutenticado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException excecaoDeAutenticacao
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
            response.getWriter(),
            new ErroResposta(HttpStatus.UNAUTHORIZED.value(), "Não autenticado")
        );
    }
}
