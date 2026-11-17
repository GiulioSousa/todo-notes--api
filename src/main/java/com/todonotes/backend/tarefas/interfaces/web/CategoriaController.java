package com.todonotes.backend.tarefas.interfaces.web;

import com.todonotes.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.todonotes.backend.tarefas.aplicacao.CategoriaService;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoCategoria;
import com.todonotes.backend.tarefas.interfaces.web.dto.RespostaCategoria;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<RespostaCategoria> listar() {
        return categoriaService.listar(UsuarioAutenticado.obterId()).stream()
            .map(RespostaCategoria::aPartirDoDominio)
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaCategoria> criar(@Valid @RequestBody RequisicaoCategoria requisicao) {
        RespostaCategoria criada = RespostaCategoria.aPartirDoDominio(
            categoriaService.criar(UsuarioAutenticado.obterId(), requisicao.nome())
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @PutMapping("/{id}")
    public RespostaCategoria renomear(
        @PathVariable Long id, @Valid @RequestBody RequisicaoCategoria requisicao
    ) {
        return RespostaCategoria.aPartirDoDominio(
            categoriaService.renomear(UsuarioAutenticado.obterId(), id, requisicao.nome())
        );
    }

    /**
     * Exclui e desetiqueta as tarefas, sem bloquear. O {@code 204} não diz quantas foram
     * afetadas — quem precisa saber recarrega a lista.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
