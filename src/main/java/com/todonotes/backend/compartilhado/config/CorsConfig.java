package com.todonotes.backend.compartilhado.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Libera as origens declaradas em {@link PropriedadesCors}, e só elas.
 *
 * <p>Sem {@code allowCredentials}: a autenticação desta API viaja no cabeçalho
 * {@code Authorization}, que o cliente monta, e não é credencial no sentido do CORS. Ligar a
 * bandeira faria o navegador anexar cookies às requisições entre origens e obrigaria o
 * servidor a ecoar a origem de volta — permissão que nada aqui usa.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesCors.class)
public class CorsConfig {

    private final PropriedadesCors propriedades;

    public CorsConfig(PropriedadesCors propriedades) {
        this.propriedades = propriedades;
    }

    @Bean
    public CorsConfigurationSource origensPermitidas() {
        CorsConfiguration configuracao = new CorsConfiguration();
        // setAllowedOrigins, e nunca setAllowedOriginPatterns: o segundo aceita curingas, e
        // um "*" aqui autorizaria qualquer site a chamar a API em nome de quem o visitasse.
        configuracao.setAllowedOrigins(propriedades.origens());
        // PATCH está na lista porque mover um card é PATCH /api/tarefas/{id}/lugar. Sem ele,
        // o navegador recusa a requisição no preflight e o arrastar não funciona entre
        // origens — falha que só aparece no desenvolvimento com as duas portas separadas.
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource origem = new UrlBasedCorsConfigurationSource();
        origem.registerCorsConfiguration("/**", configuracao);
        return origem;
    }
}
