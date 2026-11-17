package com.todonotes.backend.usuarios.aplicacao;

import com.todonotes.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.todonotes.backend.usuarios.dominio.ControleDeTentativasDeLogin.ResultadoDaTentativa;
import com.todonotes.backend.usuarios.dominio.RepositorioUsuario;
import com.todonotes.backend.usuarios.dominio.Usuario;
import com.todonotes.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.todonotes.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que se testa aqui não é "o login funciona" — isso o teste por HTTP já mostrou. É o
 * conjunto de defesas que <b>não aparecem na resposta</b>: a mensagem única, a tentativa
 * contada antes da consulta e o hash descartável. Todas são invisíveis quando funcionam, e
 * nenhuma quebra de forma visível quando deixa de funcionar.
 */
class AutenticacaoServiceTest {

    private static final String USUARIO = "teste";
    private static final String SENHA = "uma frase de senha";
    private static final String HASH_DA_CONTA = "$2a$10$hash-da-conta";
    private static final String HASH_DESCARTAVEL = "$2a$10$hash-descartavel";

    private RepositorioUsuario repositorio;
    private PasswordEncoder codificador;
    private ControleDeTentativasDeLogin controle;
    private AutenticacaoService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioUsuario.class);
        codificador = mock(PasswordEncoder.class);
        controle = mock(ControleDeTentativasDeLogin.class);

        // O serviço gera o hash descartável no construtor, pelo próprio codificador.
        when(codificador.encode(anyString())).thenReturn(HASH_DESCARTAVEL);
        when(controle.registrar(anyString())).thenReturn(new ResultadoDaTentativa(false, 0));

        servico = new AutenticacaoService(repositorio, codificador, controle);
    }

    @Test
    void autenticaEDevolveOUsuario() {
        contaExiste();
        when(codificador.matches(SENHA, HASH_DA_CONTA)).thenReturn(true);

        Usuario autenticado = servico.autenticar(USUARIO, SENHA);

        assertThat(autenticado.getId()).isEqualTo(1L);
        assertThat(autenticado.getUsuario()).isEqualTo(USUARIO);
    }

    /**
     * A defesa contra enumeração de contas. Se as duas mensagens divergissem, sondar o login
     * diria quais contas existem — e sem rota de cadastro, sondar o login é o único jeito
     * que resta de descobrir isso.
     */
    @Test
    void contaInexistenteESenhaErradaDaoAMesmaExcecao() {
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());
        String mensagemDaContaInexistente = capturarMensagemDeFalha();

        contaExiste();
        when(codificador.matches(SENHA, HASH_DA_CONTA)).thenReturn(false);
        String mensagemDaSenhaErrada = capturarMensagemDeFalha();

        assertThat(mensagemDaContaInexistente)
            .as("mensagens diferentes entregariam quais contas existem")
            .isEqualTo(mensagemDaSenhaErrada);
    }

    /**
     * Sem isto, a resposta para um nome inexistente volta sem passar pelo BCrypt e chega
     * muito antes da resposta para um nome cadastrado — diferença grande o bastante para
     * revelar, pelo relógio, quem tem conta no sistema.
     */
    @Test
    void contaInexistenteAindaConfereUmHashDescartavel() {
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA))
            .isInstanceOf(CredenciaisInvalidasException.class);

        verify(codificador).matches(eq(SENHA), eq(HASH_DESCARTAVEL));
    }

    /**
     * A ordem importa: contar depois da consulta deixaria a força bruta pagar nada por
     * tentativa em conta inexistente, e o balde serve justamente para encarecer isso.
     */
    @Test
    void aTentativaEContadaAntesDeQualquerConsulta() {
        when(controle.registrar(USUARIO)).thenReturn(new ResultadoDaTentativa(true, 42));

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA))
            .isInstanceOf(TentativasExcedidasException.class);

        verify(controle).registrar(USUARIO);
        verify(repositorio, never()).buscarPorUsuario(any());
        verify(codificador, never()).matches(any(), any());
    }

    @Test
    void oBloqueioCarregaOsSegundosParaLiberar() {
        when(controle.registrar(USUARIO)).thenReturn(new ResultadoDaTentativa(true, 42));

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA))
            .isInstanceOf(TentativasExcedidasException.class)
            .hasMessageContaining("42");
    }

    /**
     * O balde por conta zera no acerto. Sem isso, quem usa o sistema com frequência acabaria
     * barrado pelo próprio uso legítimo, e a trava puniria o dono da conta em vez de quem a
     * ataca.
     */
    @Test
    void loginBemSucedidoLiberaOBalde() {
        contaExiste();
        when(codificador.matches(SENHA, HASH_DA_CONTA)).thenReturn(true);

        servico.autenticar(USUARIO, SENHA);

        verify(controle).liberar(USUARIO);
    }

    @Test
    void senhaErradaNaoLiberaOBalde() {
        contaExiste();
        when(codificador.matches(SENHA, HASH_DA_CONTA)).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA))
            .isInstanceOf(CredenciaisInvalidasException.class);

        verify(controle, never()).liberar(any());
    }

    /**
     * O nome é canonizado antes de tudo — e "tudo" inclui a chave do balde. Contar
     * {@code Teste} e {@code teste} em baldes separados daria cinco tentativas por grafia a
     * quem alterna a caixa.
     */
    @Test
    void oNomeEhNormalizadoAntesDaContagemEDaConsulta() {
        contaExiste();
        when(codificador.matches(SENHA, HASH_DA_CONTA)).thenReturn(true);

        servico.autenticar("  TESTE  ", SENHA);

        verify(controle).registrar(USUARIO);
        verify(repositorio).buscarPorUsuario(USUARIO);
        verify(controle).liberar(USUARIO);
    }

    private void contaExiste() {
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(
            new Usuario(1L, USUARIO, HASH_DA_CONTA, Instant.parse("2026-11-17T12:00:00Z"))
        ));
    }

    private String capturarMensagemDeFalha() {
        try {
            servico.autenticar(USUARIO, SENHA);
            throw new AssertionError("esperava CredenciaisInvalidasException");
        } catch (CredenciaisInvalidasException excecao) {
            return excecao.getMessage();
        }
    }
}
