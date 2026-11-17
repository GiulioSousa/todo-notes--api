package com.todonotes.backend.compartilhado.seguranca;

import com.todonotes.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limitador de taxa por chave, baseado em token bucket.
 *
 * <p>Os baldes vivem em memória e expiram por inatividade — suficiente para uma instância
 * única, que é o desenho deste projeto. Com mais de uma instância, cada uma aplicaria o
 * limite isoladamente e o teto efetivo viraria o dobro, o triplo, conforme o número de
 * cópias; nesse dia o estado precisa migrar para armazenamento compartilhado, e o Bucket4j
 * tem adaptadores para Redis e Hazelcast.
 *
 * <p>O teto de 100 mil chaves não é afago de desempenho: sem ele, cada origem e cada nome
 * de usuário jamais vistos criariam uma entrada permanente, e tentar entrar com nomes
 * aleatórios bastaria para esgotar a memória — a defesa viraria o ataque.
 */
@Component
public class LimitadorDeRequisicoes {

    private static final Duration EXPIRACAO_POR_INATIVIDADE = Duration.ofHours(1);

    private final Cache<String, Bucket> baldesPorChave = Caffeine.newBuilder()
        .expireAfterAccess(EXPIRACAO_POR_INATIVIDADE.toMinutes(), TimeUnit.MINUTES)
        .maximumSize(100_000)
        .build();

    public ResultadoLimite verificar(String chave, Politica politica) {
        Bucket balde = baldesPorChave.get(chave, ignorada -> construirBalde(politica));
        ConsumptionProbe sonda = balde.tryConsumeAndReturnRemaining(1);

        if (sonda.isConsumed()) {
            return ResultadoLimite.permitido(sonda.getRemainingTokens());
        }

        long segundosDeEspera = Duration.ofNanos(sonda.getNanosToWaitForRefill()).toSeconds();
        return ResultadoLimite.bloqueado(Math.max(segundosDeEspera, 1));
    }

    /**
     * Descarta o balde da chave, devolvendo-lhe a cota cheia.
     *
     * <p>Serve ao limite por conta: as tentativas contam enquanto falham, e um login
     * bem-sucedido as zera. Sem isso, quem usa o sistema com frequência acabaria barrado
     * pelo próprio uso legítimo, e a trava puniria o dono da conta em vez de quem a ataca.
     */
    public void reiniciar(String chave) {
        baldesPorChave.invalidate(chave);
    }

    private Bucket construirBalde(Politica politica) {
        Bandwidth limite = Bandwidth.builder()
            .capacity(politica.tentativas())
            .refillIntervally(politica.tentativas(), politica.janela())
            .build();

        return Bucket.builder().addLimit(limite).build();
    }

    public record ResultadoLimite(boolean permitido, long tentativasRestantes, long segundosParaLiberar) {

        static ResultadoLimite permitido(long tentativasRestantes) {
            return new ResultadoLimite(true, tentativasRestantes, 0);
        }

        static ResultadoLimite bloqueado(long segundosParaLiberar) {
            return new ResultadoLimite(false, 0, segundosParaLiberar);
        }
    }
}
