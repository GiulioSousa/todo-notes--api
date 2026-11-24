package com.todonotes.backend.compartilhado.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite por origem continua sendo por <b>cliente</b> quando a aplicação está atrás de um
 * proxy reverso.
 *
 * <p>O filtro deriva o balde de {@code getRemoteAddr()}. Atrás de um proxy, esse método
 * devolve o endereço do próprio proxy para toda requisição, e os baldes de todos os clientes
 * colapsam num só: o primeiro que errasse cinco senhas trancaria o login de todo mundo.
 *
 * <p>{@code server.forward-headers-strategy=FRAMEWORK} faz o contêiner resolver o endereço
 * real a partir de {@code X-Forwarded-For} antes de a requisição chegar ao filtro. Este teste
 * fixa esse comportamento; a configuração é declarada aqui, e não no {@code application.yml},
 * porque o padrão precisa continuar sendo {@code NONE}.
 *
 * <p><b>A configuração só é segura junto de duas condições que vivem fora do código</b>: o
 * proxy sobrescreve {@code X-Forwarded-For}, e a aplicação escuta apenas em loopback. Sem as
 * duas, o cliente forja o próprio endereço — que é exatamente o que este teste faz de
 * propósito, para provar que o cabeçalho passa a ser obedecido.
 *
 * <p>Os nomes de usuário não existem: a resposta esperada é {@code 401}, e cada cliente
 * tenta uma conta diferente para que o balde por conta (10 em 15 minutos) não interfira.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.forward-headers-strategy=FRAMEWORK"
)
class EnderecoDeOrigemAtrasDeProxyTest {

    private static final int NAO_AUTORIZADO = 401;
    private static final int LIMITE_EXCEDIDO = 429;

    /** O limite de login é 5 por minuto; a sexta tentativa do mesmo cliente é barrada. */
    private static final int ALEM_DO_LIMITE = 6;

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void clientesDiferentesAtrasDoMesmoProxyNaoCompartilhamOBalde() {
        for (int tentativa = 1; tentativa < ALEM_DO_LIMITE; tentativa++) {
            assertThat(tentarLogin("203.0.113.4")).isEqualTo(NAO_AUTORIZADO);
        }

        assertThat(tentarLogin("203.0.113.4"))
            .as("o cliente que gastou as cinco tentativas é barrado")
            .isEqualTo(LIMITE_EXCEDIDO);

        assertThat(tentarLogin("198.51.100.9"))
            .as("outro cliente chega com o balde intacto — sem isso, um usuário tranca o login de todos")
            .isEqualTo(NAO_AUTORIZADO);

        assertThat(tentarLogin("203.0.113.4"))
            .as("e o bloqueio do primeiro não foi afetado pelo segundo")
            .isEqualTo(LIMITE_EXCEDIDO);
    }

    private int tentarLogin(String enderecoDoCliente) {
        HttpRequest requisicao = HttpRequest.newBuilder(
                URI.create("http://localhost:" + porta + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", enderecoDoCliente)
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"usuario\":\"proxy-" + enderecoDoCliente + "\",\"senha\":\"uma senha qualquer\"}"))
            .build();

        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (IOException excecao) {
            throw new IllegalStateException("Falha ao chamar a API", excecao);
        }
    }
}
