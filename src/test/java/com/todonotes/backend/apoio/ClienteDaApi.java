package com.todonotes.backend.apoio;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Um cliente HTTP de verdade para os testes de API, com o token já no cabeçalho.
 *
 * <p>É {@code HttpClient} do JDK contra a porta real da aplicação, e não {@code MockMvc}: o
 * que se quer testar inclui os filtros, a serialização e os códigos de status como o cliente
 * os recebe. {@code MockMvc} atalha o servidor e deixaria de fora justamente a parte que já
 * deu surpresa aqui — o `429` com `Retry-After`, por exemplo, nasce num filtro.
 *
 * <p>O GeFinX repete este ajudante em cada classe de teste. Aqui ele é um só, porque as três
 * classes de teste de API fazem exatamente as mesmas chamadas.
 */
public final class ClienteDaApi {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;
    private final String token;

    public ClienteDaApi(int porta, String token) {
        this.base = "http://localhost:" + porta;
        this.token = token;
    }

    public HttpResponse<String> get(String caminho) {
        return enviar(requisicao(caminho).GET());
    }

    public HttpResponse<String> post(String caminho, String corpo) {
        return enviar(requisicao(caminho).POST(HttpRequest.BodyPublishers.ofString(corpo)));
    }

    public HttpResponse<String> put(String caminho, String corpo) {
        return enviar(requisicao(caminho).PUT(HttpRequest.BodyPublishers.ofString(corpo)));
    }

    public HttpResponse<String> patch(String caminho, String corpo) {
        return enviar(requisicao(caminho)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(corpo)));
    }

    public HttpResponse<String> excluir(String caminho) {
        return enviar(requisicao(caminho).DELETE());
    }

    /** Sem o cabeçalho de autorização — para os casos que esperam {@code 401}. */
    public HttpResponse<String> semToken(String caminho) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho)).timeout(ESPERA).GET());
    }

    private HttpRequest.Builder requisicao(String caminho) {
        return HttpRequest.newBuilder(URI.create(base + caminho))
            .timeout(ESPERA)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");
    }

    private HttpResponse<String> enviar(HttpRequest.Builder construtor) {
        try {
            return http.send(construtor.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException excecao) {
            throw new IllegalStateException("falha ao chamar a API de teste", excecao);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("chamada de teste interrompida", excecao);
        }
    }
}
