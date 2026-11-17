package com.todonotes.backend.usuarios.infraestrutura;

import com.todonotes.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.todonotes.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.todonotes.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.todonotes.backend.usuarios.dominio.NormalizadorDeUsuario;
import org.springframework.stereotype.Component;

/**
 * Cumpre a porta do balde por conta sobre o mesmo token bucket que serve ao limite por
 * endereço, com uma família de chaves própria.
 *
 * <p>É o único ponto em que {@code usuarios} toca {@code compartilhado} fora da borda — e
 * é aceitável porque o que ele importa é infraestrutura comum, não regra de outro contexto.
 */
@Component
public class ControleDeTentativasDeLoginPorUsuario implements ControleDeTentativasDeLogin {

    private static final String PREFIXO_DA_CHAVE = "login-por-conta:";

    private final LimitadorDeRequisicoes limitador;
    private final PropriedadesLimiteDeRequisicoes.Politica politica;

    public ControleDeTentativasDeLoginPorUsuario(
        LimitadorDeRequisicoes limitador,
        PropriedadesLimiteDeRequisicoes propriedades
    ) {
        this.limitador = limitador;
        this.politica = propriedades.loginPorConta();
    }

    @Override
    public ResultadoDaTentativa registrar(String usuario) {
        var resultado = limitador.verificar(chavePara(usuario), politica);
        return new ResultadoDaTentativa(!resultado.permitido(), resultado.segundosParaLiberar());
    }

    @Override
    public void liberar(String usuario) {
        limitador.reiniciar(chavePara(usuario));
    }

    /**
     * Delega a normalização ao {@link NormalizadorDeUsuario}, o mesmo ponto que decide qual
     * conta o login enxerga. Guardar aqui uma cópia da regra a faria divergir com o tempo, e
     * bastaria alternar a caixa das letras para ganhar um balde novo a cada tentativa:
     * entrada controlada por quem ataca não pode escolher o balde.
     *
     * <p>O prefixo separa esta família da do limite por endereço. Sem ele, um nome de
     * usuário igual a um endereço IP compartilharia balde com ele.
     */
    private String chavePara(String usuario) {
        String normalizado = NormalizadorDeUsuario.normalizar(usuario);
        return PREFIXO_DA_CHAVE + (normalizado == null ? "" : normalizado);
    }
}
