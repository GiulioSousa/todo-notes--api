package com.todonotes.backend.usuarios.dominio;

import java.util.Optional;

/**
 * Porta de leitura de contas.
 *
 * <p>Um método só, e as ausências são decisão:
 *
 * <ul>
 *   <li><b>Sem {@code buscarPorId}.</b> O token carrega o id do dono, e o filtro JWT o
 *       entrega aos casos de uso sem tocar no banco. Carregar o usuário a cada requisição
 *       custaria uma consulta por chamada para conferir algo que, no MVP, nada pode
 *       invalidar — não há exclusão de conta nem revogação de sessão.</li>
 *   <li><b>Sem {@code alterarSenha}.</b> Trocar senha pela tela está fora do MVP; hoje a
 *       senha muda no banco, por quem o opera.</li>
 *   <li><b>Sem {@code gravar}.</b> A conta nasce do {@code criar-usuario.sql} (Decisão 9).
 *       Nenhum código do projeto escreve em {@code usuarios}.</li>
 * </ul>
 */
public interface RepositorioUsuario {

    /** Espera o nome já na forma canônica — ver {@link NormalizadorDeUsuario}. */
    Optional<Usuario> buscarPorUsuario(String usuario);
}
