package com.todonotes.backend.compartilhado.excecoes;

import com.todonotes.backend.tarefas.dominio.excecoes.CategoriaDuplicadaException;
import com.todonotes.backend.tarefas.dominio.excecoes.RecursoNaoEncontradoException;
import com.todonotes.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.todonotes.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class ManipuladorGlobalDeExcecoes {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorGlobalDeExcecoes.class);

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(CredenciaisInvalidasException excecao) {
        return construirResposta(HttpStatus.UNAUTHORIZED, excecao.getMessage());
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    public ResponseEntity<ErroResposta> tratarTentativasExcedidas(TentativasExcedidasException excecao) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(excecao.getSegundosParaLiberar()))
            .body(new ErroResposta(HttpStatus.TOO_MANY_REQUESTS.value(), excecao.getMessage()));
    }

    /** Não existe — ou é de outro dono, que aqui dá a mesma resposta. */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return construirResposta(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    /** Nome de categoria em uso, sem diferenciar maiúscula de minúscula. */
    @ExceptionHandler(CategoriaDuplicadaException.class)
    public ResponseEntity<ErroResposta> tratarCategoriaDuplicada(CategoriaDuplicadaException excecao) {
        return construirResposta(HttpStatus.CONFLICT, excecao.getMessage());
    }

    /** Validação do corpo. O mapa é por campo, para a tela mostrar o erro embaixo dele. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> erros = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
            .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));

        return ResponseEntity.badRequest()
            .body(new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Dados inválidos", erros));
    }

    /**
     * Validação que falha num parâmetro, e não no corpo — o caso de
     * {@code GET /api/tarefas?concluidas=...}.
     *
     * <p>Sem este manipulador, {@code @Min} ou um enum inválido num {@code @RequestParam}
     * não produzem `400`: a exceção cai no genérico do fim e volta como `500`, um erro de
     * quem chamou saindo como falha do servidor.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResposta> tratarValidacaoDeParametro(HandlerMethodValidationException excecao) {
        Map<String, String> erros = new LinkedHashMap<>();
        excecao.getParameterValidationResults().forEach(resultado -> {
            String parametro = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors().stream()
                .findFirst()
                .ifPresent(erro -> erros.put(
                    parametro == null ? "parametro" : parametro,
                    erro.getDefaultMessage()
                ));
        });

        return ResponseEntity.badRequest()
            .body(new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Dados inválidos", erros));
    }

    /**
     * Rota que não existe é erro de quem chamou, não falha do servidor — inclusive a rota
     * certa escrita com barra no fim, que o Spring 6 deixou de casar por padrão.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> tratarRotaInexistente(HttpServletRequest requisicao) {
        log.debug("Rota inexistente: {} {}", requisicao.getMethod(), requisicao.getRequestURI());
        return construirResposta(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    /**
     * Corpo que o conversor não lê: JSON truncado, campo com tipo incompatível, bytes que
     * não formam UTF-8 válido.
     *
     * <p>A mensagem é fixa de propósito. A da exceção traz posição no fluxo e nomes de
     * classes do domínio — detalhe que ajuda quem depura e desenha o sistema para quem
     * sonda. Ele fica no log, que é onde tem leitor legítimo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoIlegivel(HttpMessageNotReadableException excecao) {
        log.debug("Corpo da requisição ilegível: {}", excecao.getMessage());
        return construirResposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido");
    }

    /** O cabeçalho {@code Allow} acompanha porque a RFC 9110 o exige em `405`. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException excecao) {
        var resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);

        Set<HttpMethod> metodosAceitos = excecao.getSupportedHttpMethods();
        if (metodosAceitos != null && !metodosAceitos.isEmpty()) {
            resposta.allow(metodosAceitos.toArray(new HttpMethod[0]));
        }

        return resposta.body(new ErroResposta(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "Método não permitido para este recurso"
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarTipoNaoSuportado(HttpMediaTypeNotSupportedException excecao) {
        return construirResposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de conteúdo não suportado");
    }

    /** Trecho do caminho que não converte, como {@code /api/tarefas/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tratarParametroComTipoErrado(MethodArgumentTypeMismatchException excecao) {
        return construirResposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + excecao.getName());
    }

    /**
     * Último recurso. A resposta é genérica — detalhe de exceção em corpo vira mapa do
     * sistema para quem sonda —, mas a exceção inteira vai para o log: sem rastro, um `500`
     * não diz sequer em que camada nasceu.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroInesperado(Exception excecao, HttpServletRequest requisicao) {
        log.error("Erro inesperado em {} {}", requisicao.getMethod(), requisicao.getRequestURI(), excecao);
        return construirResposta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado");
    }

    private ResponseEntity<ErroResposta> construirResposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResposta(status.value(), mensagem));
    }
}
