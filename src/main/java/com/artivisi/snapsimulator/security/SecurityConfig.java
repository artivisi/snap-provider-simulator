package com.artivisi.snapsimulator.security;

import com.artivisi.snapsimulator.config.SimulatorProperties;
import com.artivisi.snapsimulator.repository.PartnerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

/**
 * Three areas: SNAP endpoints (token and signature, checked by SnapInboundFilter),
 * the operator admin (login from env) and the partner portal (sign-up accounts).
 * The portal JSON API also accepts HTTP Basic so scripts can drive it; it is
 * exempt from CSRF because it only takes JSON and the session cookie is SameSite=Strict.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String FRAME_POLICY = "frame-ancestors 'none'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    SecurityFilterChain snapChain(HttpSecurity http) {
        http.securityMatcher("/snap/**", "/keys/**", "/actuator/health", "/actuator/health/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.disable())
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(FRAME_POLICY)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminChain(HttpSecurity http, SimulatorProperties properties, PasswordEncoder encoder) {
        UserDetailsService operator = new InMemoryUserDetailsManager(User.withUsername(properties.operator().username())
                .password(encoder.encode(properties.operator().password())).roles("OPERATOR").build());
        http.securityMatcher("/admin/**")
                .authenticationManager(manager(operator, encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("OPERATOR"))
                .formLogin(form -> form.loginPage("/admin/login").defaultSuccessUrl("/admin", true).permitAll())
                .logout(logout -> logout.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(FRAME_POLICY)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain portalChain(HttpSecurity http, PartnerRepository partners, PasswordEncoder encoder) {
        http.authenticationManager(manager(new PartnerUserDetailsService(partners), encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/portal/login", "/portal/signup", "/portal/api/signup",
                                "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/portal/**").hasRole("PARTNER")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/portal/login").defaultSuccessUrl("/portal", true).permitAll())
                .logout(logout -> logout.logoutUrl("/portal/logout").logoutSuccessUrl("/portal/login?logout"))
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/portal/api/")))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/portal/api/**"))
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(FRAME_POLICY)));
        return http.build();
    }

    private static AuthenticationManager manager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
