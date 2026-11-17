package com.todonotes.backend.usuarios.infraestrutura;

import com.todonotes.backend.usuarios.dominio.RepositorioUsuario;
import com.todonotes.backend.usuarios.dominio.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Cumpre a porta {@link RepositorioUsuario} com Spring Data.
 *
 * <p>A tradução de {@link UsuarioJpaEntity} para {@link Usuario} acontece aqui, e é o que
 * mantém o JPA deste lado da fronteira: o caso de uso recebe domínio, não entidade
 * gerenciada — e com isso não há risco de uma alteração em objeto ainda ligado à sessão do
 * Hibernate virar {@code UPDATE} por conta própria no fim da transação.
 */
@Repository
public class RepositorioUsuarioJpaAdapter implements RepositorioUsuario {

    private final UsuarioSpringDataRepository springDataRepository;

    public RepositorioUsuarioJpaAdapter(UsuarioSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return springDataRepository.findByUsuario(usuario).map(UsuarioJpaEntity::paraDominio);
    }
}
