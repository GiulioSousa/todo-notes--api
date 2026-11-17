package com.todonotes.backend.apoio;

import com.todonotes.backend.usuarios.dominio.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Cria e apaga contas de acesso para os testes que precisam de um dono de verdade no banco.
 *
 * <p>Existe porque não há rota de cadastro (Decisão 9): a conta nasce por {@code INSERT}, do
 * mesmo jeito que nasce em produção pelo {@code db/criar-usuario.sql}. Testar contra o
 * caminho real vale mais do que a conveniência de uma rota que o sistema não tem.
 *
 * <p>Grava só o usuário. Não há semente de categorias nem de preferências — quem precisa
 * delas as cria no próprio cenário, e um teste que dependesse de semente estaria testando a
 * semente.
 */
public final class ContasDeTeste {

    /** Serve a quem precisa fazer login de verdade; a maioria dos testes forja o token. */
    public static final String SENHA = "uma frase de senha";

    private static final PasswordEncoder CODIFICADOR = new BCryptPasswordEncoder();

    private ContasDeTeste() {
    }

    /**
     * O nome tem de vir em minúsculas e sem espaço nas pontas — é o que a
     * {@code ck_usuarios_usuario_normalizado} exige, e a mesma canonização que o
     * {@code NormalizadorDeUsuario} aplica no login.
     */
    public static Usuario criar(JdbcTemplate jdbcTemplate, String nomeDeUsuario) {
        String senhaHash = CODIFICADOR.encode(SENHA);

        Long id = jdbcTemplate.queryForObject(
            "INSERT INTO usuarios (usuario, senha_hash) VALUES (?, ?) RETURNING id",
            Long.class, nomeDeUsuario, senhaHash
        );

        return new Usuario(id, nomeDeUsuario, senhaHash, Instant.now());
    }

    /**
     * Apaga as contas cujo nome começa com o prefixo, e tudo o que pende delas.
     *
     * <p><b>A ordem não é zelo: é obrigação.</b> As três chaves estrangeiras para
     * {@code usuarios} não declaram {@code ON DELETE}, então valem como {@code RESTRICT} —
     * apagar a conta antes dos filhos falha. Não há cascata de propósito: no desenho, conta
     * não é apagada, e um {@code CASCADE} silencioso num projeto de usuário único seria mais
     * risco do que conveniência.
     */
    public static void apagar(JdbcTemplate jdbcTemplate, String prefixo) {
        String donos = "SELECT id FROM usuarios WHERE usuario LIKE ?";

        jdbcTemplate.update("DELETE FROM tarefas WHERE usuario_id IN (" + donos + ")", prefixo);
        jdbcTemplate.update("DELETE FROM categorias WHERE usuario_id IN (" + donos + ")", prefixo);
        jdbcTemplate.update("DELETE FROM preferencias WHERE usuario_id IN (" + donos + ")", prefixo);
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", prefixo);
    }
}
