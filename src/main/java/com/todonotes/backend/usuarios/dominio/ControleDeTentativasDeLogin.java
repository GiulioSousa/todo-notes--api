package com.todonotes.backend.usuarios.dominio;

/**
 * Limita quantas tentativas de login uma mesma conta aceita, independentemente da origem
 * delas.
 *
 * <p>Complementa o limite por endereço, que não alcança este caso: como cada origem tem o
 * seu próprio balde, um atacante distribuído multiplica as tentativas contra uma única
 * conta apenas trocando de procedência. Aqui a chave é o alvo, não de onde veio.
 *
 * <p>Duas regras que o adaptador precisa respeitar, as duas por motivo de segurança: a
 * chave deve derivar do nome de usuário <b>normalizado</b> (ver {@link
 * NormalizadorDeUsuario}), senão variar a caixa das letras rende um balde novo a cada
 * tentativa; e a contagem deve valer também para nomes que não existem, senão a resposta de
 * bloqueio revelaria quais contas existem — justamente o que o {@code 401} de mensagem
 * única esconde.
 *
 * <p>É porta do domínio, e não serviço: quem conta é o Caffeine, na infraestrutura. O
 * domínio define o contrato e fica livre de Bucket4j.
 */
public interface ControleDeTentativasDeLogin {

    /**
     * Contabiliza uma tentativa para o nome de usuário e informa se ela ainda é permitida.
     */
    ResultadoDaTentativa registrar(String usuario);

    /**
     * Devolve a cota cheia ao nome de usuário, chamado após autenticação bem-sucedida.
     */
    void liberar(String usuario);

    record ResultadoDaTentativa(boolean bloqueado, long segundosParaLiberar) {
    }
}
