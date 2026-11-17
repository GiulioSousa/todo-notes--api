package com.todonotes.backend.compartilhado.config;

import com.todonotes.backend.compartilhado.seguranca.FiltroAutenticacaoJwt;
import com.todonotes.backend.compartilhado.seguranca.FiltroLimiteDeRequisicoes;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Impede que os filtros de segurança sejam registrados também na cadeia do Servlet.
 *
 * <p>Um {@code jakarta.servlet.Filter} exposto como bean é registrado automaticamente pelo
 * Spring Boot para todas as requisições. Como estes dois já são posicionados na cadeia do
 * Spring Security, o registro automático os duplicaria e os faria rodar fora da ordem — o
 * limite de requisições contaria duas vezes a mesma tentativa.
 */
@Configuration
public class RegistroDeFiltrosConfig {

    @Bean
    public FilterRegistrationBean<FiltroAutenticacaoJwt> desabilitarRegistroAutomaticoDoFiltroJwt(
        FiltroAutenticacaoJwt filtro
    ) {
        return desabilitar(filtro);
    }

    @Bean
    public FilterRegistrationBean<FiltroLimiteDeRequisicoes> desabilitarRegistroAutomaticoDoFiltroDeLimite(
        FiltroLimiteDeRequisicoes filtro
    ) {
        return desabilitar(filtro);
    }

    private <T extends Filter> FilterRegistrationBean<T> desabilitar(T filtro) {
        FilterRegistrationBean<T> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
