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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O modo de visualização por categoria, contra o banco de verdade e por HTTP.
 *
 * <p>A {@code GrupoDeCategoriaTest} já cobre a regra em memória. O que só aparece aqui é o
 * que depende do banco: a restrição {@code NULLS NOT DISTINCT} aguentando o grupo das sem
 * etiqueta, a renumeração chegando ao disco, e — o motivo de tudo isto existir — <b>as duas
 * ordens não se encostando</b>.
 *
 * <p>Os dois testes que importam são o {@link #arrastarPorCategoriaNaoMexeNaOrdemDasFaixas} e
 * o {@link #arrastarNaFaixaNaoMexeNaOrdemPorCategoria}: é o que o dono pediu, e é o tipo de
 * coisa que erra em silêncio — a outra tela só aparece embaralhada quando alguém a abre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArrastarPorCategoriaTest {

    private static final String PREFIXO = "arrastar-categoria-";

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

    /**
     * Grupo vazio vem na resposta, como as faixas vazias vêm: é área de soltura, e é por ele
     * que se põe o primeiro card numa categoria recém-criada.
     */
    @Test
    void aListaTrazUmGrupoPorCategoriaEOSemEtiquetaNoFim() throws Exception {
        long casa = criarCategoria("Casa");
        criarCategoria("Trabalho");
        long solta = criar("Solta", "ALTA", null);
        long daCasa = criar("Da casa", "ALTA", casa);

        JsonNode grupos = gruposDe(api.get("/api/tarefas/por-categoria").body());

        assertThat(grupos).hasSize(3);
        assertThat(grupos).extracting(ArrastarPorCategoriaTest::nomeDoGrupo)
            .as("por nome, e o grupo sem etiqueta no fim")
            .containsExactly("Casa", "Trabalho", null);
        conferirGrupo(grupos, "Casa", daCasa);
        conferirGrupo(grupos, "Trabalho");
        conferirGrupo(grupos, null, solta);
    }

    @Test
    void moverDentroDoGrupoRenumeraEPersiste() throws Exception {
        long casa = criarCategoria("Casa");
        long a = criar("A", "ALTA", casa);
        long b = criar("B", "MEDIA", casa);
        long c = criar("C", "BAIXA", casa);

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar-na-categoria",
            "{\"categoriaId\":" + casa + ",\"posicao\":2}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode grupos = gruposDe(resposta.body());
        assertThat(grupos).as("so o grupo afetado volta").hasSize(1);
        conferirGrupo(grupos, "Casa", b, c, a);

        conferirGrupo(gruposDe(api.get("/api/tarefas/por-categoria").body()), "Casa", b, c, a);
    }

    /** Soltar o card em outro grupo é o que troca a etiqueta dele. */
    @Test
    void moverParaOutroGrupoTrocaAEtiqueta() throws Exception {
        long casa = criarCategoria("Casa");
        long trabalho = criarCategoria("Trabalho");
        long a = criar("A", "ALTA", casa);
        long b = criar("B", "ALTA", casa);
        long doTrabalho = criar("Do trabalho", "ALTA", trabalho);

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar-na-categoria",
            "{\"categoriaId\":" + trabalho + ",\"posicao\":0}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode grupos = gruposDe(resposta.body());
        assertThat(grupos).as("os dois grupos afetados voltam").hasSize(2);
        conferirGrupo(grupos, "Casa", b);
        conferirGrupo(grupos, "Trabalho", a, doTrabalho);

        assertThat(tarefaNoGrupo(grupos, "Trabalho", 0).get("categoria").get("nome").asString())
            .as("inserir impoe a categoria do grupo de destino")
            .isEqualTo("Trabalho");
    }

    @Test
    void moverParaOGrupoSemEtiquetaDesetiqueta() throws Exception {
        long casa = criarCategoria("Casa");
        long a = criar("A", "ALTA", casa);

        HttpResponse<String> resposta = api.patch("/api/tarefas/" + a + "/lugar-na-categoria",
            "{\"categoriaId\":null,\"posicao\":0}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        conferirGrupo(gruposDe(resposta.body()), null, a);
        assertThat(tarefaNoGrupo(gruposDe(resposta.body()), null, 0).get("categoria").isNull())
            .as("o card perde a etiqueta")
            .isTrue();
    }

    /**
     * <b>O teste que o pedido do dono exige.</b> Cada modo guarda a sua própria ordem, e
     * mudar a ordem num deles não altera a do outro.
     */
    @Test
    void arrastarPorCategoriaNaoMexeNaOrdemDasFaixas() throws Exception {
        long casa = criarCategoria("Casa");
        long a = criar("A", "ALTA", casa);
        long b = criar("B", "ALTA", casa);
        long c = criar("C", "ALTA", casa);

        assertThat(api.patch("/api/tarefas/" + a + "/lugar-na-categoria",
            "{\"categoriaId\":" + casa + ",\"posicao\":2}").statusCode()).isEqualTo(OK);

        conferirGrupo(gruposDe(api.get("/api/tarefas/por-categoria").body()), "Casa", b, c, a);
        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", a, b, c);
    }

    /** E a volta: a faixa é dona só da ordem dela. */
    @Test
    void arrastarNaFaixaNaoMexeNaOrdemPorCategoria() throws Exception {
        long casa = criarCategoria("Casa");
        long a = criar("A", "ALTA", casa);
        long b = criar("B", "ALTA", casa);
        long c = criar("C", "ALTA", casa);

        assertThat(api.patch("/api/tarefas/" + a + "/lugar",
            "{\"prioridade\":\"ALTA\",\"posicao\":2}").statusCode()).isEqualTo(OK);

        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", b, c, a);
        conferirGrupo(gruposDe(api.get("/api/tarefas/por-categoria").body()), "Casa", a, b, c);
    }

    /**
     * A tarefa nova nasce no fim dos <b>dois</b> grupos a que passa a pertencer — não passa
     * na frente do que já foi ordenado em nenhum dos dois modos.
     */
    @Test
    void criarPoeNoFimDasDuasOrdens() throws Exception {
        long casa = criarCategoria("Casa");
        criar("Alta da casa", "ALTA", casa);
        criar("Media sem etiqueta", "MEDIA", null);

        HttpResponse<String> resposta = api.post("/api/tarefas",
            "{\"titulo\":\"Nova\",\"prioridade\":\"ALTA\",\"categoriaId\":" + casa + "}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        JsonNode criada = objectMapper.readTree(resposta.body());
        assertThat(criada.get("posicaoNaPrioridade").asInt())
            .as("fim da faixa ALTA, que ja tinha uma")
            .isEqualTo(1);
        assertThat(criada.get("posicaoNaCategoria").asInt())
            .as("fim do grupo Casa, que ja tinha uma")
            .isEqualTo(1);
    }

    /**
     * Trocar a categoria pelo formulário move o card para o fim do grupo de destino, e deixa
     * a ordem da faixa onde estava. É o espelho de trocar a prioridade, que cai no
     * {@code PATCH /lugar}.
     */
    @Test
    void editarTrocandoACategoriaMoveParaOFimDoGrupoDeDestino() throws Exception {
        long casa = criarCategoria("Casa");
        long trabalho = criarCategoria("Trabalho");
        long jaNoTrabalho = criar("Ja no trabalho", "ALTA", trabalho);
        long a = criar("A", "ALTA", casa);
        long b = criar("B", "ALTA", casa);

        HttpResponse<String> resposta = api.put("/api/tarefas/" + a,
            "{\"titulo\":\"A editada\",\"categoriaId\":" + trabalho + "}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode grupos = gruposDe(api.get("/api/tarefas/por-categoria").body());
        conferirGrupo(grupos, "Casa", b);
        conferirGrupo(grupos, "Trabalho", jaNoTrabalho, a);
        conferirFaixa(faixasDe(api.get("/api/tarefas").body()), "ALTA", jaNoTrabalho, a, b);
    }

    /**
     * <b>As conferências 5 e 6 do {@code db/conferir-v2.sql}, agora como código.</b> As
     * tarefas que perdem a etiqueta caem no grupo das sem categoria levando as posições que
     * tinham no grupo que deixou de existir; sem a renumeração, a restrição
     * {@code uq_tarefas_ordem_na_categoria} aborta o {@code DELETE} no commit.
     *
     * <p>A tarefa criada depois prova a outra metade: com buraco ou repetição nas posições, a
     * contagem que acha o fim do grupo devolveria um lugar já ocupado.
     */
    @Test
    void excluirCategoriaPoeAsOrfasNoFimDoGrupoSemEtiqueta() throws Exception {
        long casa = criarCategoria("Casa");
        long solta = criar("Solta", "ALTA", null);
        long daCasa = criar("Da casa", "ALTA", casa);
        long outraDaCasa = criar("Outra da casa", "MEDIA", casa);

        assertThat(api.excluir("/api/categorias/" + casa).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode grupos = gruposDe(api.get("/api/tarefas/por-categoria").body());
        assertThat(grupos).as("a categoria deixou de existir").hasSize(1);
        conferirGrupo(grupos, null, solta, daCasa, outraDaCasa);

        long nova = criar("Nova", "BAIXA", null);
        conferirGrupo(
            gruposDe(api.get("/api/tarefas/por-categoria").body()),
            null, solta, daCasa, outraDaCasa, nova
        );
    }

    /** O mesmo {@code 404} das outras rotas, pela mesma razão: não confirmar que o id existe. */
    @Test
    void tarefaDeOutroDonoDa404() {
        long minha = criar("Minha", "ALTA", null);
        ClienteDaApi outroDono = clienteDeUmNovoDono();

        assertThat(outroDono.patch("/api/tarefas/" + minha + "/lugar-na-categoria",
            "{\"categoriaId\":null,\"posicao\":0}").statusCode()).isEqualTo(NAO_ENCONTRADO);
    }

    /** Categoria de destino que não é do dono também é {@code 404}, e não {@code 400}. */
    @Test
    void categoriaDeDestinoDeOutroDonoDa404() {
        ClienteDaApi outroDono = clienteDeUmNovoDono();
        long categoriaAlheia = criarCategoria(outroDono, "Alheia");
        long minha = criar("Minha", "ALTA", null);

        assertThat(api.patch("/api/tarefas/" + minha + "/lugar-na-categoria",
            "{\"categoriaId\":" + categoriaAlheia + ",\"posicao\":0}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
    }

    private ClienteDaApi clienteDeUmNovoDono() {
        Usuario dono = ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime());
        return new ClienteDaApi(porta, jwtService.gerarToken(dono).token());
    }

    private long criarCategoria(String nome) {
        return criarCategoria(api, nome);
    }

    private long criarCategoria(ClienteDaApi cliente, String nome) {
        HttpResponse<String> resposta = cliente.post("/api/categorias",
            "{\"nome\":\"" + nome + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private long criar(String titulo, String prioridade, Long categoriaId) {
        HttpResponse<String> resposta = api.post("/api/tarefas",
            "{\"titulo\":\"" + titulo + "\",\"prioridade\":\"" + prioridade + "\""
                + ",\"categoriaId\":" + categoriaId + "}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private JsonNode gruposDe(String corpo) {
        return objectMapper.readTree(corpo).get("grupos");
    }

    private JsonNode faixasDe(String corpo) {
        return objectMapper.readTree(corpo).get("faixas");
    }

    /** Confere a ordem dos ids e as posições 0..n-1 de um grupo de categoria da resposta. */
    private void conferirGrupo(JsonNode grupos, String nome, long... idsEsperados) {
        JsonNode tarefas = grupoDe(grupos, nome).get("tarefas");

        assertThat(tarefas).as("tamanho do grupo %s", nome).hasSize(idsEsperados.length);
        for (int indice = 0; indice < idsEsperados.length; indice++) {
            JsonNode tarefa = tarefas.get(indice);

            assertThat(tarefa.get("id").asLong())
                .as("id no indice %d do grupo %s", indice, nome)
                .isEqualTo(idsEsperados[indice]);
            assertThat(tarefa.get("posicaoNaCategoria").asInt())
                .as("posicao no grupo do id %s em %s", tarefa.get("id"), nome)
                .isEqualTo(indice);
        }
    }

    /** O mesmo para uma faixa, para os dois testes que olham as duas ordens. */
    private void conferirFaixa(JsonNode faixas, String prioridade, long... idsEsperados) {
        JsonNode tarefas = faixaDe(faixas, prioridade).get("tarefas");

        assertThat(tarefas).as("tamanho da faixa %s", prioridade).hasSize(idsEsperados.length);
        for (int indice = 0; indice < idsEsperados.length; indice++) {
            JsonNode tarefa = tarefas.get(indice);

            assertThat(tarefa.get("id").asLong())
                .as("id no indice %d da faixa %s", indice, prioridade)
                .isEqualTo(idsEsperados[indice]);
            assertThat(tarefa.get("posicaoNaPrioridade").asInt())
                .as("posicao na faixa do id %s em %s", tarefa.get("id"), prioridade)
                .isEqualTo(indice);
        }
    }

    private JsonNode tarefaNoGrupo(JsonNode grupos, String nome, int indice) {
        return grupoDe(grupos, nome).get("tarefas").get(indice);
    }

    /** {@code nome} nulo é o grupo das tarefas sem etiqueta. */
    private JsonNode grupoDe(JsonNode grupos, String nome) {
        for (JsonNode grupo : grupos) {
            if (Objects.equals(nome, nomeDoGrupo(grupo))) {
                return grupo;
            }
        }
        throw new AssertionError("o grupo " + nome + " nao veio na resposta: " + grupos);
    }

    private JsonNode faixaDe(JsonNode faixas, String prioridade) {
        for (JsonNode faixa : faixas) {
            if (prioridade.equals(faixa.get("prioridade").asString())) {
                return faixa;
            }
        }
        throw new AssertionError("a faixa " + prioridade + " nao veio na resposta: " + faixas);
    }

    private static String nomeDoGrupo(JsonNode grupo) {
        JsonNode categoria = grupo.get("categoria");
        return categoria == null || categoria.isNull() ? null : categoria.get("nome").asString();
    }
}
