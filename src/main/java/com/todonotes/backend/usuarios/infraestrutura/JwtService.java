package com.todonotes.backend.usuarios.infraestrutura;

import com.todonotes.backend.usuarios.dominio.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emite e confere os tokens de acesso.
 *
 * <p>Sem estado: o token é a prova, e nada é guardado do lado do servidor. A consequência é
 * que <b>um token entregue não pode ser recolhido</b> antes de expirar — por isso a
 * expiração de 24 horas, e por isso a revogação de sessões, que o GeFinX tem, é uma
 * ausência registrada no {@code execucao.md} e não um esquecimento.
 */
@Component
public class JwtService {

    /** Mínimo de bytes que o HS384 exige da chave. */
    private static final int BYTES_MINIMOS_DA_CHAVE = 48;

    private final SecretKey chaveSecreta;
    private final long expiracaoMinutos;

    /**
     * O algoritmo <b>não</b> é escolhido no código: o JJWT o deriva do tamanho da chave, e
     * 48 bytes é o que faz dele HS384. Uma chave de 32 bytes viraria HS256 sem erro, sem
     * aviso e sem deixar de funcionar — a documentação diria uma coisa e o sistema faria
     * outra. A conferência abaixo existe para que isso seja impossível em vez de
     * improvável, e falha na partida, não na primeira tentativa de login.
     */
    public JwtService(
        @Value("${todonotes.jwt.secret}") String segredoBase64,
        @Value("${todonotes.jwt.expiracao-minutos}") long expiracaoMinutos
    ) {
        byte[] bytesDaChave = Decoders.BASE64.decode(segredoBase64);
        if (bytesDaChave.length < BYTES_MINIMOS_DA_CHAVE) {
            throw new IllegalStateException(
                "A chave de todonotes.jwt.secret precisa de no mínimo " + BYTES_MINIMOS_DA_CHAVE
                    + " bytes para o HS384; a informada tem " + bytesDaChave.length + "."
            );
        }

        this.chaveSecreta = Keys.hmacShaKeyFor(bytesDaChave);
        this.expiracaoMinutos = expiracaoMinutos;
    }

    /**
     * Devolve o token e o instante em que ele deixa de valer.
     *
     * <p>Os dois juntos porque a resposta do login publica {@code expiraEm}, e recalcular o
     * prazo fora daqui abriria a chance de o número informado ao cliente não ser o mesmo que
     * foi assinado dentro do token.
     */
    public TokenEmitido gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(expiracaoMinutos, ChronoUnit.MINUTES);

        String token = Jwts.builder()
            .subject(usuario.getId().toString())
            .claim("usuario", usuario.getUsuario())
            .issuedAt(Date.from(agora))
            .expiration(Date.from(expiraEm))
            .signWith(chaveSecreta)
            .compact();

        return new TokenEmitido(token, expiraEm);
    }

    /**
     * Diz se o token abre a sessão. Conferido por HTTP em 2026-11-17: corpo, cabeçalho ou
     * assinatura alterados, assinatura removida e texto que não é JWT dão todos
     * {@code 401}.
     *
     * <p><b>Um caractere a mais no fim do token é aceito, e não é falha.</b> A assinatura
     * HS384 tem 48 bytes, exatamente 64 caracteres em Base64; o 65º carrega 6 bits órfãos,
     * que não completam um byte e são descartados na decodificação — a assinatura resultante
     * é byte a byte a mesma, e o token é o mesmo token. Dois caracteres a mais já produzem
     * um byte extra e caem no {@code 401}, e qualquer alteração no conteúdo muda o texto
     * assinado e também cai. É canonicalização de Base64, não brecha de assinatura, e está
     * escrito aqui porque um teste que acrescenta um caractere ao token parece revelar um
     * buraco que não existe.
     */
    public boolean tokenValido(String token) {
        try {
            extrairIdUsuario(token);
            return true;
        } catch (JwtException | IllegalArgumentException excecao) {
            return false;
        }
    }

    /**
     * O {@code sub} carrega o id, e é dele que sai o dono de toda consulta do sistema. Por
     * isso a leitura passa obrigatoriamente por {@code verifyWith}: um token não verificado
     * é texto que o cliente escreveu.
     */
    public Long extrairIdUsuario(String token) {
        String assunto = Jwts.parser()
            .verifyWith(chaveSecreta)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();

        return Long.valueOf(assunto);
    }

    public record TokenEmitido(String token, Instant expiraEm) {
    }
}
