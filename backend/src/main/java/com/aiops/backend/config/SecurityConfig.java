package com.aiops.backend.config;

import com.aiops.backend.auth.AppUserDetailsService;
import com.aiops.backend.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AppUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless JWT API: no session cookie, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())
                        )
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage())
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/components/enabled", "/api/syslog-sources/enabled").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/components/*/status").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/netflow/sources",
                                "/api/netflow/flows",
                                "/api/netflow/import-runs",
                                "/api/incidents/*/network-flows"
                        ).hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/netflow/import/latest",
                                "/api/netflow/import/sample",
                                "/api/netflow/sources"
                        ).hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/netflow/sources/*").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/netflow/sources/*/enable").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/netflow/sources/*").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
