package com.todonotes.backend.compartilhado.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trilha de auditoria: quem tentou entrar, quando e com que resultado.
 *
 * <p>Usa um logger de nome próprio, separado dos loggers por classe, porque a trilha
 * responde a uma pergunta diferente da do log de aplicação e sobrevive a decisões
 * diferentes: abaixar o nível de {@code com.todonotes.backend} para calar a depuração não
 * pode, junto, apagar o registro de quem entrou. O nome fixo permite endereçá-la sozinha em
 * {@code logging.level} e isolá-la num arquivo próprio quando for preciso.
 *
 * <p>Registra login aceito, login recusado — distinguindo conta inexistente de senha
 * incorreta —, bloqueio por tentativas na conta e o {@code 429} por origem. A senha nunca
 * entra, em circunstância alguma. O nome de usuário entra, por ser o único identificador da
 * tentativa, inclusive das que não correspondem a conta nenhuma, que são justamente as que
 * interessam ao investigar força bruta.
 *
 * <p>A distinção entre "conta inexistente" e "senha incorreta" que a trilha faz e a resposta
 * HTTP não faz não é contradição: a resposta vai para quem tentou entrar, e a trilha, para
 * quem opera o sistema. Apagar a distinção aqui não protegeria ninguém e cegaria o único
 * leitor legítimo dela.
 */
public final class Auditoria {

    /** Nome do logger, endereçável em {@code logging.level}. */
    public static final String NOME = "AUDITORIA";

    public static final Logger LOG = LoggerFactory.getLogger(NOME);

    /**
     * Teto de um valor na trilha.
     *
     * <p>O dobro do maior valor legítimo que chega aqui — o nome de usuário, limitado a 60
     * caracteres na borda e na coluna. A folga é proposital: truncar exatamente no limite
     * legítimo esconderia que o valor veio maior, e com o dobro a reticência no fim do
     * registro é, ela mesma, o sinal de que alguém mandou algo fora do tamanho aceito.
     */
    private static final int LIMITE_DE_CARACTERES = 120;

    private Auditoria() {
    }

    /**
     * Prepara um valor vindo do cliente para entrar na trilha.
     *
     * <p>Cada evento ocupa exatamente uma linha, e um valor com {@code \n} forjaria uma
     * segunda linha, inventando um evento que nunca aconteceu. A validação da borda
     * recusaria esse valor antes de chegar aqui, mas uma trilha que depende de quem a chama
     * para não ser falsificada não é trilha.
     *
     * <p>Neutraliza <b>todo caractere de controle</b>, e não só quebra de linha — é um
     * ponto em que este projeto vai além do GeFinX, que troca apenas {@code [\r\n]}. O que
     * sobra lá é o {@code ESC}: um nome de usuário contendo {@code \u001b[2K} reescreve a
     * linha anterior em qualquer terminal que leia o log, apagando o registro de uma
     * tentativa sem alterar byte nenhum do arquivo. {@code \p{Cntrl}} cobre o intervalo
     * ASCII de controle inteiro e deixa passar acento, que é texto legítimo.
     */
    public static String seguro(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "-";
        }

        String neutralizado = valor.replaceAll("\\p{Cntrl}", "_");
        return neutralizado.length() <= LIMITE_DE_CARACTERES
            ? neutralizado
            : neutralizado.substring(0, LIMITE_DE_CARACTERES) + "...";
    }
}
