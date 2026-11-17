package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Preferencias;
import com.todonotes.backend.tarefas.dominio.RepositorioPreferencias;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RepositorioPreferenciasJpaAdapter implements RepositorioPreferencias {

    private final PreferenciasSpringDataRepository springDataRepository;

    public RepositorioPreferenciasJpaAdapter(PreferenciasSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Insere ou atualiza, e quem decide é o id: o padrão vem sem id e nasce como linha nova.
     * Não há {@code upsert} escrito à mão porque o caso de uso sempre lê antes — e é a
     * leitura que traz o id quando a linha existe.
     */
    @Override
    public Preferencias salvar(Preferencias preferencias) {
        return springDataRepository.save(PreferenciasJpaEntity.deDominio(preferencias)).paraDominio();
    }

    @Override
    public Optional<Preferencias> buscarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioId(usuarioId)
            .map(PreferenciasJpaEntity::paraDominio);
    }
}
