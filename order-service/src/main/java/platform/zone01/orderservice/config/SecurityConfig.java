package platform.zone01.orderservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import platform.zone01.commonsecurity.jwt.JwtAuthFilter;
import platform.zone01.orderservice.dto.ErrorResponseDTO;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CLIENT = "CLIENT";
    private static final String SELLER = "SELLER";

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/cart/**").hasRole(CLIENT)
                        .requestMatchers(HttpMethod.POST, "/orders/checkout").hasRole(CLIENT)
                        .requestMatchers(HttpMethod.GET, "/orders").hasRole(CLIENT)
                        .requestMatchers(HttpMethod.GET, "/orders/seller").hasRole(SELLER)
                        .requestMatchers(HttpMethod.PUT, "/orders/*/status").hasRole(SELLER)
                        .requestMatchers(HttpMethod.DELETE, "/orders/*").hasRole(CLIENT)
                        .requestMatchers(HttpMethod.POST, "/orders/*/redo").hasRole(CLIENT)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", request))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied", request))
                )
                .build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String message, HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(), status.value(), message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
