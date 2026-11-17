package com.todonotes.backend.usuarios.interfaces.web;

import com.todonotes.backend.usuarios.aplicacao.AutenticacaoService;
import com.todonotes.backend.usuarios.dominio.Usuario;
import com.todonotes.backend.usuarios.infraestrutura.JwtService;
import com.todonotes.backend.usuarios.interfaces.web.dto.RequisicaoLogin;
import com.todonotes.backend.usuarios.interfaces.web.dto.RespostaAutenticacao;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só login. Não há cadastro (Decisão 9) nem logout — o token é sem estado, e sair é
 * descartá-lo no navegador.
 */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final JwtService jwtService;

    public AutenticacaoController(AutenticacaoService autenticacaoService, JwtService jwtService) {
        this.autenticacaoService = autenticacaoService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<RespostaAutenticacao> login(@Valid @RequestBody RequisicaoLogin requisicao) {
        Usuario usuario = autenticacaoService.autenticar(requisicao.usuario(), requisicao.senha());
        var emitido = jwtService.gerarToken(usuario);

        return ResponseEntity.ok(new RespostaAutenticacao(
            emitido.token(), usuario.getUsuario(), emitido.expiraEm()
        ));
    }
}
