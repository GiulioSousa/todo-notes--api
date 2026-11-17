package com.todonotes.backend.tarefas.interfaces.web;

import com.todonotes.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.todonotes.backend.tarefas.aplicacao.TarefaComCategoria;
import com.todonotes.backend.tarefas.aplicacao.TarefaService;
import com.todonotes.backend.tarefas.dominio.FiltroDeConcluidas;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoEdicaoTarefa;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoLugar;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoNovaTarefa;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoStatus;
import com.todonotes.backend.tarefas.interfaces.web.dto.RespostaListaDeTarefas;
import com.todonotes.backend.tarefas.interfaces.web.dto.RespostaTarefa;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As seis rotas das tarefas.
 *
 * <p><b>O {@code usuario_id} nunca vem do corpo nem da URL</b> — sai sempre do token, por
 * {@link UsuarioAutenticado}. É o que impede alguém de ler ou mover tarefa de outro dono
 * trocando um número, e é por isso que não existe {@code /api/usuarios/{id}/tarefas}.
 */
@RestController
@RequestMapping("/api/tarefas")
public class TarefaController {

    private final TarefaService tarefaService;

    public TarefaController(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    /**
     * A lista, nas três faixas.
     *
     * <p>O padrão de {@code concluidas} é {@code PADRAO} — a preferência do dono. Está
     * escrito como texto porque {@code defaultValue} exige constante de compilação, e é o
     * único lugar do código em que um valor deste enum aparece soletrado.
     */
    @GetMapping
    public RespostaListaDeTarefas listar(
        @RequestParam(required = false) Long categoriaId,
        @RequestParam(defaultValue = "PADRAO") FiltroDeConcluidas concluidas
    ) {
        return RespostaListaDeTarefas.aPartirDoDominio(
            tarefaService.listar(UsuarioAutenticado.obterId(), categoriaId, concluidas)
        );
    }

    @PostMapping
    public ResponseEntity<RespostaTarefa> criar(@Valid @RequestBody RequisicaoNovaTarefa requisicao) {
        TarefaComCategoria criada = tarefaService.criar(
            UsuarioAutenticado.obterId(), requisicao.titulo(), requisicao.descricao(),
            requisicao.prioridade(), requisicao.categoriaId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(RespostaTarefa.aPartirDoDominio(criada));
    }

    @PutMapping("/{id}")
    public RespostaTarefa editar(
        @PathVariable Long id, @Valid @RequestBody RequisicaoEdicaoTarefa requisicao
    ) {
        return RespostaTarefa.aPartirDoDominio(tarefaService.editar(
            UsuarioAutenticado.obterId(), id,
            requisicao.titulo(), requisicao.descricao(), requisicao.categoriaId()
        ));
    }

    /**
     * O arrastar. Responde com as faixas afetadas — uma, quando o card não troca de
     * prioridade; duas, quando troca —, no mesmo formato da listagem. Assim o cliente não
     * precisa recarregar a lista inteira nem confiar na própria conta de posições.
     */
    @PatchMapping("/{id}/lugar")
    public RespostaListaDeTarefas mover(
        @PathVariable Long id, @Valid @RequestBody RequisicaoLugar requisicao
    ) {
        return RespostaListaDeTarefas.aPartirDoDominio(tarefaService.mover(
            UsuarioAutenticado.obterId(), id, requisicao.prioridade(), requisicao.posicao()
        ));
    }

    @PatchMapping("/{id}/status")
    public RespostaTarefa alterarStatus(
        @PathVariable Long id, @Valid @RequestBody RequisicaoStatus requisicao
    ) {
        return RespostaTarefa.aPartirDoDominio(
            tarefaService.alterarStatus(UsuarioAutenticado.obterId(), id, requisicao.status())
        );
    }

    /** Exclusão definitiva: não há lixeira. A faixa é renumerada pelo caso de uso. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        tarefaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
