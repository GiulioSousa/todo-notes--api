package com.todonotes.backend.compartilhado.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O health check responde sem token na porta de monitoramento, e só lá.
 *
 * <p>Fixa as três decisões do bloco {@code management} e da cadeia de monitoramento do
 * {@link SecurityConfig}, porque cada uma regride sem barulho:
 *
 * <ul>
 *   <li><b>Sem token, {@code 200}.</b> Se a cadeia do Actuator sumir ou perder a ordem, a
 *       cadeia da API alcança a porta de monitoramento e o health vira {@code 401} — e o
 *       {@code curl} do roteiro de deploy esperaria para sempre.</li>
 *   <li><b>Sem detalhe no corpo.</b> Só o status; nada sobre banco, disco ou versão.</li>
 *   <li><b>Fora da porta da API.</b> É ela que o nginx publica; se o Actuator voltasse para
 *       lá, o health ficaria acessível da internet.</li>
 * </ul>
 *
 * <p>Com {@code RANDOM_PORT}, o Spring sorteia também a porta de monitoramento, então este
 * teste não disputa a 8081 com uma aplicação rodando na máquina.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoramentoTest {

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int portaDaApi;

    @LocalManagementPort
    private int portaDeMonitoramento;

    @Test
    void healthRespondeSemTokenESemDetalhes() {
        HttpResponse<String> resposta = get(portaDeMonitoramento, "/actuator/health");

        assertThat(resposta.statusCode()).isEqualTo(200);
        // O Spring Boot 4 lista os nomes dos grupos de probe (liveness, readiness), o mesmo
        // corpo que o GeFinX responde em produção. Nome de grupo não é detalhe; banco, disco
        // e versão seriam.
        assertThat(resposta.body())
            .contains("\"status\":\"UP\"")
            .doesNotContain("components", "details", "db", "diskSpace");
    }

    @Test
    void healthNaoExisteNaPortaDaApi() {
        assertThat(portaDeMonitoramento).isNotEqualTo(portaDaApi);
        assertThat(get(portaDaApi, "/actuator/health").statusCode())
            .as("na porta publicada pelo proxy, a rota cai na cadeia da API e pede token")
            .isEqualTo(401);
    }

    @Test
    void outrosEndpointsNaoEstaoExpostos() {
        assertThat(get(portaDeMonitoramento, "/actuator/env").statusCode())
            .as("endpoint não exposto não casa com a cadeia de monitoramento, e não responde")
            .isIn(401, 404);
    }

    private HttpResponse<String> get(int porta, String caminho) {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
            .GET()
            .build();
        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (IOException excecao) {
            throw new IllegalStateException("Falha ao chamar a aplicação", excecao);
        }
    }
}
