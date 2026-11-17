package com.todonotes.backend.tarefas.interfaces.web;

import com.todonotes.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.todonotes.backend.tarefas.aplicacao.PreferenciaService;
import com.todonotes.backend.tarefas.interfaces.web.dto.RequisicaoPreferencias;
import com.todonotes.backend.tarefas.interfaces.web.dto.RespostaPreferencias;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurso singular: {@code /api/preferencias}, sem {@code /{id}}. São as preferências do
 * dono do token, e não uma coleção.
 *
 * <p>O {@code GET} <b>nunca</b> dá {@code 404}: sem linha no banco, devolve o padrão. O
 * {@code PUT} é inserção ou atualização, e é ele que cria a linha na primeira vez.
 */
@RestController
@RequestMapping("/api/preferencias")
public class PreferenciaController {

    private final PreferenciaService preferenciaService;

    public PreferenciaController(PreferenciaService preferenciaService) {
        this.preferenciaService = preferenciaService;
    }

    @GetMapping
    public RespostaPreferencias buscar() {
        return RespostaPreferencias.aPartirDoDominio(
            preferenciaService.buscar(UsuarioAutenticado.obterId())
        );
    }

    @PutMapping
    public RespostaPreferencias gravar(@Valid @RequestBody RequisicaoPreferencias requisicao) {
        return RespostaPreferencias.aPartirDoDominio(preferenciaService.gravar(
            UsuarioAutenticado.obterId(),
            requisicao.ocultarConcluidasAutomaticamente(),
            requisicao.diasParaOcultar()
        ));
    }
}
