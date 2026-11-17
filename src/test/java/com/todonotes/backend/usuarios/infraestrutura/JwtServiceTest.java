package com.todonotes.backend.usuarios.infraestrutura;

import com.todonotes.backend.usuarios.dominio.Usuario;
import com.todonotes.backend.usuarios.infraestrutura.JwtService.TokenEmitido;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O token é a única credencial que o sistema aceita depois do login, e a leitura dele é o
 * ponto onde "quem é o dono desta requisição" é decidido. Os testes aqui cercam as duas
 * pontas: a chave, que falha na partida, e a assinatura, que falha na leitura.
 */
class JwtServiceTest {

    /** Exatamente 48 bytes em ASCII — o mínimo do HS384. */
    private static final String SEGREDO_DE_48_BYTES = "chave-de-teste-com-48-bytes-exatos-para-o-hs384!";

    private static final long EXPIRACAO_EM_MINUTOS = 60;

    private static final Usuario DONO = new Usuario(
        7L, "teste", "$2a$10$irrelevante-aqui", Instant.parse("2026-11-17T12:00:00Z")
    );

    /**
     * Falha na partida, e não na primeira tentativa de login: uma chave curta derruba a
     * aplicação ao subir, onde o erro é óbvio, em vez de produzir tokens fracos em silêncio.
     */
    @Test
    void chaveCurtaERecusadaNaPartida() {
        String curta = emBase64(SEGREDO_DE_48_BYTES.substring(0, 47));

        assertThatThrownBy(() -> new JwtService(curta, EXPIRACAO_EM_MINUTOS))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("48")
            .hasMessageContaining("47");
    }

    @Test
    void oTokenCarregaOIdDoDono() {
        JwtService servico = servico();

        TokenEmitido emitido = servico.gerarToken(DONO);

        assertThat(servico.extrairIdUsuario(emitido.token())).isEqualTo(7L);
        assertThat(servico.tokenValido(emitido.token())).isTrue();
    }

    @Test
    void oTokenExpiraNoPrazoConfigurado() {
        TokenEmitido emitido = servico().gerarToken(DONO);

        Duration prazo = Duration.between(Instant.now(), emitido.expiraEm());

        assertThat(prazo).isBetween(
            Duration.ofMinutes(EXPIRACAO_EM_MINUTOS).minusMinutes(1),
            Duration.ofMinutes(EXPIRACAO_EM_MINUTOS)
        );
    }

    @Test
    void tokenComOCorpoAlteradoERecusado() {
        JwtService servico = servico();
        String token = servico.gerarToken(DONO).token();

        String adulterado = trocarPrimeiroCaractereDoSegmento(token, 1);

        assertThat(servico.tokenValido(adulterado)).isFalse();
        assertThatThrownBy(() -> servico.extrairIdUsuario(adulterado))
            .as("corpo alterado precisa falhar na leitura, nao devolver outro id")
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenComAAssinaturaAlteradaERecusado() {
        JwtService servico = servico();
        String token = servico.gerarToken(DONO).token();

        assertThat(servico.tokenValido(trocarPrimeiroCaractereDoSegmento(token, 2))).isFalse();
    }

    @Test
    void tokenSemAssinaturaERecusado() {
        JwtService servico = servico();
        String token = servico.gerarToken(DONO).token();
        String semAssinatura = token.substring(0, token.lastIndexOf('.') + 1);

        assertThat(servico.tokenValido(semAssinatura)).isFalse();
    }

    /**
     * O caso que importa de verdade: a chave é o que separa um token emitido por este
     * sistema de um token que alguém montou.
     */
    @Test
    void tokenAssinadoComOutraChaveERecusado() {
        String outroSegredo = emBase64("OUTRA-chave-com-48-bytes-exatos-para-o-hs384!!!!");
        String tokenDeOutraOrigem = new JwtService(outroSegredo, EXPIRACAO_EM_MINUTOS)
            .gerarToken(DONO).token();

        assertThat(servico().tokenValido(tokenDeOutraOrigem)).isFalse();
    }

    @Test
    void textoQueNaoEUmJwtERecusado() {
        JwtService servico = servico();

        assertThat(servico.tokenValido("nao-e-um-jwt")).isFalse();
        assertThat(servico.tokenValido("")).isFalse();
        assertThat(servico.tokenValido("a.b.c")).isFalse();
    }

    /**
     * Comportamento surpreendente, e correto — ver o Javadoc de
     * {@link JwtService#tokenValido(String)}. A assinatura HS384 tem 48 bytes, exatamente 64
     * caracteres em Base64; o 65º carrega 6 bits que não completam um byte e é descartado na
     * decodificação, então a assinatura é byte a byte a mesma. <b>Dois</b> caracteres a mais
     * já produzem um byte extra e caem.
     *
     * <p>O teste existe para fixar isso: se uma versão futura do JJWT passar a recusar o
     * caractere órfão, ele falha, e quem o ler aqui entende por que ele existia em vez de
     * descobrir um buraco que não há.
     */
    @Test
    void umCaractereAMaisNaAssinaturaNaoMudaOToken() {
        JwtService servico = servico();
        String token = servico.gerarToken(DONO).token();

        assertThat(servico.tokenValido(token + "x")).isTrue();
        assertThat(servico.tokenValido(token + "xx")).isFalse();
    }

    private static JwtService servico() {
        return new JwtService(emBase64(SEGREDO_DE_48_BYTES), EXPIRACAO_EM_MINUTOS);
    }

    private static String emBase64(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** Troca o primeiro caractere de um segmento do JWT, sem mudar o tamanho dele. */
    private static String trocarPrimeiroCaractereDoSegmento(String token, int indiceDoSegmento) {
        String[] segmentos = token.split("\\.");
        String alvo = segmentos[indiceDoSegmento];
        char primeiro = alvo.charAt(0);

        segmentos[indiceDoSegmento] = (primeiro == 'A' ? 'B' : 'A') + alvo.substring(1);
        return String.join(".", segmentos);
    }
}
