package com.todonotes.backend.compartilhado.config;

import com.todonotes.backend.compartilhado.seguranca.FiltroAutenticacaoJwt;
import com.todonotes.backend.compartilhado.seguranca.FiltroLimiteDeRequisicoes;
import com.todonotes.backend.compartilhado.seguranca.PontoDeEntradaNaoAutenticado;
import com.todonotes.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(PropriedadesLimiteDeRequisicoes.class)
public class SecurityConfig {

    private final FiltroAutenticacaoJwt filtroAutenticacaoJwt;
    private final FiltroLimiteDeRequisicoes filtroLimiteDeRequisicoes;
    private final CorsConfigurationSource origensPermitidas;
    private final PontoDeEntradaNaoAutenticado pontoDeEntradaNaoAutenticado;

    public SecurityConfig(
        FiltroAutenticacaoJwt filtroAutenticacaoJwt,
        FiltroLimiteDeRequisicoes filtroLimiteDeRequisicoes,
        CorsConfigurationSource origensPermitidas,
        PontoDeEntradaNaoAutenticado pontoDeEntradaNaoAutenticado
    ) {
        this.filtroAutenticacaoJwt = filtroAutenticacaoJwt;
        this.filtroLimiteDeRequisicoes = filtroLimiteDeRequisicoes;
        this.origensPermitidas = origensPermitidas;
        this.pontoDeEntradaNaoAutenticado = pontoDeEntradaNaoAutenticado;
    }

    /**
     * A cadeia do Actuator, antes da principal. Sem ela, o {@code anyRequest().authenticated()}
     * da cadeia abaixo também alcança a porta de monitoramento e {@code /actuator/health}
     * responde {@code 401} — e um health check que exige token não responde a ninguém,
     * porque quem supervisiona o processo pergunta se ele está vivo justamente quando não há
     * sessão alguma.
     *
     * <p>Liberar é seguro porque o endpoint não está exposto: vive numa porta própria ligada
     * a {@code 127.0.0.1} (bloco {@code management} do application.yml), fora do proxy e do
     * firewall, e o corpo não traz nada além de UP ou DOWN.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain cadeiaDeMonitoramento(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(autorizacao -> autorizacao.anyRequest().permitAll());

        return http.build();
    }

    /**
     * A cadeia da API.
     *
     * <p>{@code csrf} desligado é consequência de não haver sessão em cookie: sem cookie, o
     * navegador não anexa credencial a requisição de outro site, e não há o que falsificar.
     *
     * <p>O limite de requisições vem <b>antes</b> do filtro de autenticação. Na ordem
     * inversa, cada tentativa de força bruta ainda pagaria o custo de validar um token que
     * não existe antes de ser barrada.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(origensPermitidas))
            .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(tratamento -> tratamento.authenticationEntryPoint(pontoDeEntradaNaoAutenticado))
            .authorizeHttpRequests(autorizacao -> autorizacao
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(filtroAutenticacaoJwt, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(filtroLimiteDeRequisicoes, FiltroAutenticacaoJwt.class);

        return http.build();
    }

    /**
     * BCrypt com o custo padrão do Spring, 10.
     *
     * <p><b>Este custo precisa acompanhar o {@code gen_salt('bf', 10)} do
     * {@code db/criar-usuario.sql}</b>, que é onde as senhas são gravadas, já que não há
     * cadastro. Divergir não quebra o login — o {@code matches} lê o custo de dentro do hash
     * guardado —, quebra a defesa contra enumeração: este bean governa o único
     * {@code encode()} da aplicação, o hash descartável do {@code AutenticacaoService}, e com
     * o script gravando em custo maior o caminho da conta inexistente fica mais barato que o
     * da conta real.
     */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
