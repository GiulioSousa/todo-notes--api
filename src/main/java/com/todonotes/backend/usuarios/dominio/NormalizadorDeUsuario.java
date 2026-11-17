package com.todonotes.backend.usuarios.dominio;

import java.util.Locale;

/**
 * Forma canônica de um nome de usuário dentro do sistema.
 *
 * <p>A unicidade compara a string gravada, então {@code Giulivan} e {@code giulivan} seriam
 * duas contas distintas. Reduzir tudo a uma forma só na entrada é o que impede isso — e é
 * também o que faz alguém digitar o próprio nome com a caixa "errada" e ainda assim entrar.
 *
 * <p>A tabela guarda a mesma invariante numa {@code CHECK} da V1, e isso não é redundância:
 * a conta nasce de um {@code INSERT} feito à mão, sem a borda da aplicação no caminho, e um
 * nome gravado fora da forma canônica seria uma conta que o login nunca encontraria.
 *
 * <p>Existe como ponto único porque mais de uma coisa vai depender dele. Hoje só o login
 * procura por aqui; quando o limite de tentativas entrar — hoje uma lacuna conhecida,
 * registrada no {@code execucao.md} —, o balde tem de ser indexado por esta mesma forma.
 * Se as duas definições divergirem, a trava passa a ser contornável só alternando a caixa
 * das letras.
 */
public final class NormalizadorDeUsuario {

    private NormalizadorDeUsuario() {
    }

    /**
     * O {@link Locale#ROOT} não é preciosismo: {@code toLowerCase()} sem locale usa o da
     * máquina, e em turco o minúsculo de {@code I} é {@code ı}. A mesma entrada
     * normalizaria diferente conforme onde a aplicação roda, e duas instâncias
     * discordariam sobre qual conta é qual.
     *
     * <p>{@code trim()}, e não {@code strip()}, para acompanhar o {@code btrim} da
     * {@code CHECK}, que corta espaço e não todo branco Unicode. Divergir aqui não abre
     * brecha — no máximo o login não encontra a conta —, mas as duas regras descrevem a
     * mesma forma canônica e devem dizer a mesma coisa.
     */
    public static String normalizar(String usuario) {
        return usuario == null ? null : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
