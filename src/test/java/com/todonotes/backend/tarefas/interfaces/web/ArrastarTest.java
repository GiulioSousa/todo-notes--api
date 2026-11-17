package com.todonotes.backend.tarefas.interfaces.web;

import com.todonotes.backend.apoio.ClienteDaApi;
import com.todonotes.backend.apoio.ContasDeTeste;
import com.todonotes.backend.usuarios.dominio.Usuario;
import com.todonotes.backend.usuarios.infraestrutura.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O arrastar, contra o banco de verdade e por HTTP.
 *
 * <p>A {@code FaixaDePrioridadeTest} já cobre a regra em memória. O que só aparece aqui é o
 * que depende do banco: a renumeração chegando ao disco, a restrição única deferida
 * aguentando a troca de posições, e o {@code 404} que esconde a tarefa de outro dono.
 *
 * <p>O token é forjado pelo {@code JwtService}, sem passar pelo login — o balde de cinco
 * tentativas por minuto é por origem, e um login por teste esgotaria a cota em cinco testes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArrastarTest {

    private static final String PREFIXO = "arrastar-";

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int NAO_ENCONTRADO = 404;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int porta;

    private ClienteDaApi api;

    @BeforeEach
    void preparar() {
        api = clienteDeUmNovoDono();
    }

    @AfterEach
    void limpar() {
        ContasDeTeste.apagar(jdbcTemplate, PREFIXO + "%");
    }

    @Test
    void moverDentroDaFaixaRenumeraEPersiste() throws Exception {
        long a = criar("A", "ALTA");
        long b = criar("B", "ALTA");
        long c = criar("C", "ALTA");

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar",
            "{\"prioridade\":\"ALTA\",\"posicao\":2}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode faixas = faixasDe(resposta.body());
        assertThat(faixas).as("so a faixa afetada volta").hasSize(1);
        conferirFaixa(faixas, "ALTA", b, c, a);

        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", b, c, a);
    }

    @Test
    void moverEntreFaixasRenumeraAsDuas() throws Exception {
        long a = criar("A", "ALTA");
        long b = criar("B", "ALTA");
        long c = criar("C", "MEDIA");

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar",
            "{\"prioridade\":\"MEDIA\",\"posicao\":0}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode faixas = faixasDe(resposta.body());
        assertThat(faixas).as("as duas faixas afetadas voltam").hasSize(2);
        conferirFaixa(faixas, "ALTA", b);
        conferirFaixa(faixas, "MEDIA", a, c);

        assertThat(tarefaNaFaixa(faixas, "MEDIA", 0).get("prioridade").asString())
            .as("inserir impoe a prioridade da faixa de destino")
            .isEqualTo("MEDIA");
    }

    /**
     * O cliente pode estar um passo atrás da realidade, e um arrastar que falha por isso é
     * pior que um arrastar que acomoda.
     */
    @Test
    void posicaoAlemDoFimEAjustadaParaOFim() throws Exception {
        long a = criar("A", "ALTA");
        long b = criar("B", "ALTA");

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar",
            "{\"prioridade\":\"ALTA\",\"posicao\":99}");

        assertThat(resposta.statusCode()).as("ajusta, nao recusa").isEqualTo(OK);
        conferirFaixa(faixasDe(resposta.body()), "ALTA", b, a);
    }

    @Test
    void posicaoNegativaVaiParaOComeco() throws Exception {
        long a = criar("A", "ALTA");
        long b = criar("B", "ALTA");

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + b + "/lugar",
            "{\"prioridade\":\"ALTA\",\"posicao\":-5}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        conferirFaixa(faixasDe(resposta.body()), "ALTA", b, a);
    }

    /**
     * <b>O teste que justifica a renumeração na exclusão.</b> Sem ela as posições ficam com
     * buraco (0, 1, 3), e a contagem que a criação usa para achar o fim da faixa devolve um
     * lugar já ocupado: a tarefa seguinte esbarra na restrição única, no commit, e o erro
     * aparece longe da exclusão que o causou.
     */
    @Test
    void excluirRenumeraAFaixaEANovaTarefaNaoColide() throws Exception {
        long a = criar("A", "ALTA");
        long b = criar("B", "ALTA");
        long c = criar("C", "ALTA");

        assertThat(api.excluir("/api/tarefas/" + b).statusCode()).isEqualTo(SEM_CONTEUDO);
        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", a, c);

        long d = criar("D", "ALTA");

        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", a, c, d);
    }

    /**
     * {@code 404}, e não {@code 403}: um {@code 403} confirmaria que aquele id existe, e
     * quem quisesse contar as tarefas do sistema só precisaria somar as duas respostas.
     */
    @Test
    void tarefaDeOutroDonoDa404() {
        long minha = criar("Minha", "ALTA");
        ClienteDaApi outroDono = clienteDeUmNovoDono();

        assertThat(outroDono.patch("/api/tarefas/" + minha + "/lugar",
            "{\"prioridade\":\"BAIXA\",\"posicao\":0}").statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(outroDono.patch("/api/tarefas/" + minha + "/status",
            "{\"status\":\"CONCLUIDA\"}").statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(outroDono.put("/api/tarefas/" + minha,
            "{\"titulo\":\"Sequestrada\"}").statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(outroDono.excluir("/api/tarefas/" + minha).statusCode())
            .isEqualTo(NAO_ENCONTRADO);
    }

    @Test
    void aListaTrazSempreAsTresFaixas() throws Exception {
        criar("Unica", "MEDIA");

        JsonNode faixas = faixasDe(api.get("/api/tarefas").body());

        assertThat(faixas).hasSize(3);
        assertThat(faixas).extracting(faixa -> faixa.get("prioridade").asString())
            .as("na ordem de declaracao do ENUM, nao alfabetica")
            .containsExactly("ALTA", "MEDIA", "BAIXA");
    }

    private ClienteDaApi clienteDeUmNovoDono() {
        Usuario dono = ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime());
        return new ClienteDaApi(porta, jwtService.gerarToken(dono).token());
    }

    private long criar(String titulo, String prioridade) {
        HttpResponse<String> resposta = api.post("/api/tarefas",
            "{\"titulo\":\"" + titulo + "\",\"prioridade\":\"" + prioridade + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private JsonNode faixasDe(String corpo) {
        return objectMapper.readTree(corpo).get("faixas");
    }

    /** Confere a ordem dos ids e as posições 0..n-1 de uma faixa da resposta. */
    private void conferirFaixa(JsonNode faixas, String prioridade, long... idsEsperados) {
        JsonNode tarefas = faixaDe(faixas, prioridade).get("tarefas");

        assertThat(tarefas).as("tamanho da faixa %s", prioridade).hasSize(idsEsperados.length);
        for (int indice = 0; indice < idsEsperados.length; indice++) {
            JsonNode tarefa = tarefas.get(indice);

            assertThat(tarefa.get("id").asLong())
                .as("id no indice %d da faixa %s", indice, prioridade)
                .isEqualTo(idsEsperados[indice]);
            assertThat(tarefa.get("posicao").asInt())
                .as("posicao do id %s na faixa %s", tarefa.get("id"), prioridade)
                .isEqualTo(indice);
        }
    }

    private JsonNode tarefaNaFaixa(JsonNode faixas, String prioridade, int indice) {
        return faixaDe(faixas, prioridade).get("tarefas").get(indice);
    }

    private JsonNode faixaDe(JsonNode faixas, String prioridade) {
        for (JsonNode faixa : faixas) {
            if (prioridade.equals(faixa.get("prioridade").asString())) {
                return faixa;
            }
        }
        throw new AssertionError("a faixa " + prioridade + " nao veio na resposta: " + faixas);
    }
}
