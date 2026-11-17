package com.todonotes.backend.tarefas.infraestrutura;

import com.todonotes.backend.tarefas.dominio.Categoria;
import com.todonotes.backend.tarefas.dominio.RepositorioCategoria;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioCategoriaJpaAdapter implements RepositorioCategoria {

    private final CategoriaSpringDataRepository springDataRepository;

    public RepositorioCategoriaJpaAdapter(CategoriaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Categoria salvar(Categoria categoria) {
        return springDataRepository.save(CategoriaJpaEntity.deDominio(categoria)).paraDominio();
    }

    @Override
    public Optional<Categoria> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId)
            .map(CategoriaJpaEntity::paraDominio);
    }

    @Override
    public List<Categoria> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
            .map(CategoriaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<Categoria> buscarPorNomeEUsuario(String nome, Long usuarioId) {
        return springDataRepository.buscarPorNomeIgnorandoCaixa(usuarioId, nome)
            .map(CategoriaJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }
}
