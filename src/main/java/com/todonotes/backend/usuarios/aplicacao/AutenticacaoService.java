package com.todonotes.backend.usuarios.aplicacao;

import com.todonotes.backend.compartilhado.auditoria.Auditoria;
import com.todonotes.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.todonotes.backend.usuarios.dominio.NormalizadorDeUsuario;
import com.todonotes.backend.usuarios.dominio.RepositorioUsuario;
import com.todonotes.backend.usuarios.dominio.Usuario;
import com.todonotes.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.todonotes.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacaoService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ControleDeTentativasDeLogin controleDeTentativas;

    /**
     * Hash descartável, conferido quando o nome de usuário informado não existe.
     *
     * <p>Sem ele, a resposta para um nome inexistente volta sem passar pelo BCrypt e chega
     * muito antes da resposta para um nome cadastrado — diferença grande o bastante para
     * revelar, pelo relógio, quem tem conta no sistema, ainda que a mensagem de erro seja a
     * mesma nos dois casos.
     *
     * <p>É gerado pelo próprio codificador, e não fixado no código, para acompanhar sempre
     * o custo configurado do BCrypt: um hash com custo defasado reabriria a diferença de
     * tempo que este campo existe para eliminar. O mesmo vale na outra ponta — o
     * {@code gen_salt('bf', 10)} do {@code criar-usuario.sql} precisa acompanhar o custo do
     * {@code PasswordEncoder}, ou as contas reais passam a responder mais rápido que as
     * inexistentes.
     */
    private final String hashDescartavel;

    public AutenticacaoService(
        RepositorioUsuario repositorioUsuario,
        PasswordEncoder codificadorDeSenha,
        ControleDeTentativasDeLogin controleDeTentativas
    ) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.controleDeTentativas = controleDeTentativas;
        this.hashDescartavel = codificadorDeSenha.encode(UUID.randomUUID().toString());
    }

    /**
     * A tentativa é contabilizada <b>antes</b> de qualquer consulta, e a cota só é devolvida
     * ao fim de uma autenticação bem-sucedida. Ficar entre os dois pontos — senha errada ou
     * nome inexistente — consome a tentativa, que é justamente o que encarece a força bruta.
     *
     * <p>O bloqueio vale para qualquer nome submetido, exista ele ou não. Contabilizar
     * apenas contas reais transformaria a resposta de bloqueio em confirmação de que a
     * conta existe, reabrindo por outro caminho a enumeração que o {@code 401} de mensagem
     * única fecha. Isso pesa mais aqui do que pesaria em outro projeto: sem rota de
     * cadastro, sondar o login é o único jeito que resta de descobrir quais contas há.
     *
     * <p>Devolve o {@link Usuario} autenticado; quem transforma isso em token é a borda.
     */
    public Usuario autenticar(String usuario, String senha) {
        String usuarioNormalizado = NormalizadorDeUsuario.normalizar(usuario);

        var tentativa = controleDeTentativas.registrar(usuarioNormalizado);
        if (tentativa.bloqueado()) {
            Auditoria.LOG.warn(
                "login bloqueado usuario={} liberaEm={}s",
                Auditoria.seguro(usuarioNormalizado), tentativa.segundosParaLiberar()
            );
            throw new TentativasExcedidasException(tentativa.segundosParaLiberar());
        }

        Optional<Usuario> usuarioEncontrado = repositorioUsuario.buscarPorUsuario(usuarioNormalizado);

        if (usuarioEncontrado.isEmpty()) {
            codificadorDeSenha.matches(senha, hashDescartavel);
            Auditoria.LOG.info(
                "login recusado usuario={} motivo=conta-inexistente",
                Auditoria.seguro(usuarioNormalizado)
            );
            throw new CredenciaisInvalidasException();
        }

        Usuario encontrado = usuarioEncontrado.get();
        if (!codificadorDeSenha.matches(senha, encontrado.getSenhaHash())) {
            Auditoria.LOG.info(
                "login recusado id={} usuario={} motivo=senha-incorreta",
                encontrado.getId(), Auditoria.seguro(usuarioNormalizado)
            );
            throw new CredenciaisInvalidasException();
        }

        controleDeTentativas.liberar(usuarioNormalizado);
        Auditoria.LOG.info(
            "login aceito id={} usuario={}",
            encontrado.getId(), Auditoria.seguro(usuarioNormalizado)
        );
        return encontrado;
    }
}
