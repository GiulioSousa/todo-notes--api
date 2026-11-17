package com.todonotes.backend.usuarios.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Um método, e nenhuma consulta escrita à mão.
 *
 * <p>O GeFinX tem aqui três {@code @Query}, todas a serviço de coisas que este projeto não
 * tem: revogação de sessões e troca de senha pela tela. Enquanto o login for a única
 * operação sobre contas, a derivação pelo nome do método basta.
 */
public interface UsuarioSpringDataRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    /** O nome chega na forma canônica — a normalização acontece no caso de uso. */
    Optional<UsuarioJpaEntity> findByUsuario(String usuario);
}
