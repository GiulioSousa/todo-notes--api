package com.todonotes.backend.usuarios.dominio;

import java.time.Instant;

/**
 * Conta de acesso ao sistema.
 *
 * <p>Esta entidade só é <b>lida</b>. Não há rota de cadastro (Decisão 9), e a conta nasce
 * pelo {@code backend/db/criar-usuario.sql}, rodado por quem opera o banco. Por isso não
 * existe aqui construtor de criação, validação de campo nem método que altere estado: a
 * linha chega do banco já conferida pelas {@code CHECK} da tabela, e nenhum código do
 * projeto escreve em {@code usuarios}.
 *
 * <p>Sem dependência de Spring ou JPA, como toda a camada de domínio.
 */
public class Usuario {

    private final Long id;

    /**
     * Nome de usuário na forma canônica: minúsculo e sem espaços nas pontas.
     *
     * <p>É o que identifica a conta no login. A mesma regra está na {@code CHECK} da
     * tabela e no {@link NormalizadorDeUsuario}, que trata o que chega da requisição — sem
     * ele, {@code Giulivan} não encontraria a conta de {@code giulivan}.
     *
     * <p>Não há e-mail. Endereço que o sistema nunca verifica nem usa para enviar nada não
     * identifica melhor que isto, e guardaria um dado pessoal a mais.
     */
    private final String usuario;

    /**
     * Hash BCrypt da senha, no formato {@code $2a$10$...}.
     *
     * <p>A entidade <b>expõe</b> o hash em vez de oferecer um {@code senhaConfere(...)}, e
     * isso é deliberado: conferir BCrypt é trabalho do {@code PasswordEncoder} do Spring
     * Security, que esta camada não pode importar. Quem compara é o
     * {@code AutenticacaoService}, na camada de aplicação, onde o framework é permitido.
     *
     * <p>O custo do hash (o {@code 10}) é lido de dentro do próprio valor na comparação, e
     * por isso uma conta antiga continua abrindo se o custo da aplicação mudar. O que
     * precisa acompanhar é o {@code gen_salt('bf', N)} do {@code criar-usuario.sql}: o caso
     * de uso confere um hash descartável quando a conta não existe, para que a resposta
     * demore o mesmo tanto, e custo menor aqui tornaria a conta inexistente mais rápida que
     * a real — entregando, no tempo, a informação que o {@code 401} esconde.
     */
    private final String senhaHash;

    /**
     * Quando a conta foi criada.
     *
     * <p>{@link Instant}, e não {@code LocalDateTime} como no GeFinX: a coluna é
     * {@code TIMESTAMPTZ}, e este é o par que não perde o fuso no caminho. A API publica
     * datas em ISO-8601 UTC, e {@code diasParaOcultar} é duração contada em UTC — as três
     * coisas só fecham com um tipo que carrega o instante, não a parede do relógio.
     */
    private final Instant criadoEm;

    public Usuario(Long id, String usuario, String senhaHash, Instant criadoEm) {
        this.id = id;
        this.usuario = usuario;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
