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
 * As categorias, com o foco no que o banco decide e o código apenas acompanha: a unicidade
 * do nome sem diferenciar maiúscula de minúscula, e o {@code ON DELETE SET NULL} que
 * desetiqueta as tarefas em vez de impedir a exclusão.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoriaTest {

    private static final String PREFIXO = "categoria-";

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;

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

    /**
     * A regra do índice {@code (usuario_id, lower(nome))}. Sem ela, a lista acumularia três
     * etiquetas que a pessoa lê como uma só.
     */
    @Test
    void nomeRepetidoSemDiferenciarCaixaDa409() {
        criarCategoria("Casa");

        HttpResponse<String> resposta = api.post("/api/categorias", "{\"nome\":\"casa\"}");

        assertThat(resposta.statusCode()).isEqualTo(CONFLITO);
        assertThat(resposta.body()).contains("categoria chamada").contains("casa");
        assertThat(api.post("/api/categorias", "{\"nome\":\"CASA\"}").statusCode())
            .isEqualTo(CONFLITO);
    }

    /**
     * O outro lado da mesma regra: corrigir a grafia da própria categoria não pode dar
     * conflito contra ela mesma — é o que o {@code idPermitido} do serviço existe para
     * resolver.
     */
    @Test
    void corrigirACaixaDoProprioNomeNaoDa409() throws Exception {
        long casa = criarCategoria("Casa");

        HttpResponse<String> resposta = api.put("/api/categorias/" + casa, "{\"nome\":\"casa\"}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        assertThat(objectMapper.readTree(resposta.body()).get("nome").asString())
            .as("a grafia digitada e a que fica")
            .isEqualTo("casa");
    }

    /**
     * Excluir não bloqueia, mesmo em uso: as tarefas perdem a etiqueta pelo
     * {@code ON DELETE SET NULL (categoria_id)} — o recurso de coluna específica do
     * PostgreSQL 15, que é a construção mais incomum da `V1`.
     */
    @Test
    void excluirACategoriaDesetiquetaATarefaSemApagaLa() throws Exception {
        long casa = criarCategoria("Casa");
        long tarefa = criarTarefa("Com etiqueta", casa);

        assertThat(api.excluir("/api/categorias/" + casa).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode cartao = primeiraTarefaDaFaixa("ALTA");
        assertThat(cartao.get("id").asLong()).as("a tarefa continua existindo").isEqualTo(tarefa);
        assertThat(cartao.get("categoria").isNull()).as("e sem etiqueta").isTrue();
    }

    @Test
    void aTarefaNasceComACategoriaEmbutida() throws Exception {
        long casa = criarCategoria("Casa");
        criarTarefa("Com etiqueta", casa);

        JsonNode categoria = primeiraTarefaDaFaixa("ALTA").get("categoria");

        assertThat(categoria.get("id").asLong()).isEqualTo(casa);
        assertThat(categoria.get("nome").asString()).isEqualTo("Casa");
    }

    @Test
    void aListaVemOrdenadaPorNome() throws Exception {
        criarCategoria("Zebra");
        criarCategoria("Casa");
        criarCategoria("Mercado");

        JsonNode lista = objectMapper.readTree(api.get("/api/categorias").body());

        assertThat(lista).extracting(categoria -> categoria.get("nome").asString())
            .containsExactly("Casa", "Mercado", "Zebra");
    }

    @Test
    void categoriaDeOutroDonoDa404() {
        long minha = criarCategoria("Minha");
        ClienteDaApi outroDono = clienteDeUmNovoDono();

        assertThat(outroDono.put("/api/categorias/" + minha, "{\"nome\":\"Sequestrada\"}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(outroDono.excluir("/api/categorias/" + minha).statusCode())
            .isEqualTo(NAO_ENCONTRADO);
    }

    /**
     * A categoria entra na tarefa por id, e o id é conferido contra o dono: sem isso,
     * trocar um número no corpo etiquetaria a própria tarefa com a categoria de outra pessoa
     * — e o nome dela apareceria no card.
     */
    @Test
    void criarTarefaComCategoriaDeOutroDonoDa404() {
        long deOutro = criarCategoria("Do outro");
        ClienteDaApi outroDono = clienteDeUmNovoDono();

        HttpResponse<String> resposta = outroDono.post("/api/tarefas",
            "{\"titulo\":\"Tentativa\",\"prioridade\":\"ALTA\",\"categoriaId\":" + deOutro + "}");

        assertThat(resposta.statusCode()).isEqualTo(NAO_ENCONTRADO);
    }

    private ClienteDaApi clienteDeUmNovoDono() {
        Usuario dono = ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime());
        return new ClienteDaApi(porta, jwtService.gerarToken(dono).token());
    }

    private long criarCategoria(String nome) {
        HttpResponse<String> resposta = api.post("/api/categorias", "{\"nome\":\"" + nome + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private long criarTarefa(String titulo, long categoriaId) {
        HttpResponse<String> resposta = api.post("/api/tarefas",
            "{\"titulo\":\"" + titulo + "\",\"prioridade\":\"ALTA\",\"categoriaId\":" + categoriaId + "}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private JsonNode primeiraTarefaDaFaixa(String prioridade) {
        for (JsonNode faixa : objectMapper.readTree(api.get("/api/tarefas").body()).get("faixas")) {
            if (prioridade.equals(faixa.get("prioridade").asString())) {
                return faixa.get("tarefas").get(0);
            }
        }
        throw new AssertionError("a faixa " + prioridade + " nao veio na resposta");
    }
}
