package in.nitw.minigit.config;

import in.nitw.minigit.auth.JwtAuthFilter;
import in.nitw.minigit.auth.JwtService;
import in.nitw.minigit.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. JwtAuthFilter (U1) runs in every mode; set MINIGIT_OPEN_DEV=false to make
 * the endpoints below require a token. Repo-level checks (Read/Write/Owner, SRS Table 2) live in
 * access.RepoAccess. Missing/expired token = 401 UNAUTHENTICATED in the standard ApiError shape.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties props;
    private final JwtService jwt;
    private final ObjectMapper json;

    public SecurityConfig(AppProperties props, JwtService jwt, ObjectMapper json) {
        this.props = props;
        this.jwt = jwt;
        this.json = json;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())                 // JWT in header, no cookies
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> write(res, 401, "UNAUTHENTICATED", "Please log in"))
                .accessDeniedHandler((req, res, ex) -> write(res, 403, "FORBIDDEN", "You don't have permission to do that")));

        if (props.security().openDev()) {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/**", "/api/v1/health").permitAll()
                // Guests can browse public repos (U4 for Guest, explore/search)
                .requestMatchers(HttpMethod.GET, "/api/v1/explore/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/repos/*/*/**").permitAll() // service checks visibility
                .anyRequest().authenticated());
        return http.build();
    }

    private void write(HttpServletResponse res, int status, String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        json.writeValue(res.getOutputStream(), ApiError.of(status, code, message));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);              // cost 10 (to confirm)
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
