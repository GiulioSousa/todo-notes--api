package com.todonotes.backend.compartilhado.seguranca;

import com.todonotes.backend.usuarios.infraestrutura.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Põe o id do dono no contexto de segurança, a partir do token.
 *
 * <p>Token ausente ou inválido <b>não</b> vira erro aqui: o contexto fica sem autenticação,
 * e quem responde `401` é o {@link PontoDeEntradaNaoAutenticado}. Assim há um só lugar que
 * escreve a recusa, no formato da API.
 *
 * <p>Nenhuma consulta ao banco. O token é a prova, e o id vem dele — é o que mantém a
 * autenticação sem estado e sem custo por requisição. O preço está registrado: sem consulta,
 * não há como recolher um token antes de ele expirar.
 */
@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public FiltroAutenticacaoJwt(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String cabecalhoAutorizacao = request.getHeader("Authorization");

        if (cabecalhoAutorizacao != null && cabecalhoAutorizacao.startsWith(PREFIXO_BEARER)) {
            String token = cabecalhoAutorizacao.substring(PREFIXO_BEARER.length());

            if (jwtService.tokenValido(token)) {
                Long usuarioId = jwtService.extrairIdUsuario(token);
                var autenticacao = new UsernamePasswordAuthenticationToken(usuarioId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            }
        }

        filterChain.doFilter(request, response);
    }
}
