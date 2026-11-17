package com.todonotes.backend.compartilhado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * As origens autorizadas a chamar esta API de outro domínio.
 *
 * <p>Vem de configuração, e não do código: um endereço de {@code localhost} compilado dentro
 * do artefato não tem como estar certo em produção.
 *
 * <p>Servindo frontend e API sob o mesmo domínio, esta lista deixa de ser exercida —
 * requisição de mesma origem não passa por CORS. Ela existe para o desenvolvimento, com
 * {@code ng serve} na 4200 e a API na 8080, e para o caso de o frontend ganhar domínio
 * próprio.
 *
 * @param origens como o navegador as envia: esquema, host e porta, sem barra final. Vazia
 *                recusa qualquer requisição entre origens.
 */
@ConfigurationProperties(prefix = "todonotes.cors")
public record PropriedadesCors(List<String> origens) {

    public PropriedadesCors {
        origens = origens == null ? List.of() : List.copyOf(origens);
    }
}
