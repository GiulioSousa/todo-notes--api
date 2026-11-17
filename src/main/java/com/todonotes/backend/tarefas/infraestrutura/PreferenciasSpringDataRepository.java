package com.todonotes.backend.tarefas.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciasSpringDataRepository extends JpaRepository<PreferenciasJpaEntity, Long> {

    Optional<PreferenciasJpaEntity> findByUsuarioId(Long usuarioId);
}
