package com.campuscompanion.common.security;

import com.campuscompanion.common.config.StorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.util.StringUtils;

/**
 * Security for the whole application: deny by default, server-side sessions, CSRF protection and
 * security headers. The rules come from docs/10-authorization-matrix.md and
 * docs/11-security-plan.md. Lockout and rate limits are added in task T1-07, and the
 * password-change restriction in task T1-08.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiSecurityErrorHandler errorHandler,
      SecurityContextRepository securityContextRepository,
      StorageProperties storageProperties)
      throws Exception {
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/login.html", "/css/**", "/js/**", "/favicon.ico")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**", "/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/student/**")
                    .hasRole("STUDENT")
                    .anyRequest()
                    .authenticated())
        // The CSRF token travels in the XSRF-TOKEN cookie and comes back in the X-XSRF-TOKEN
        // header. It is required on every state-changing request, including login.
        .csrf(csrf -> csrf.spa())
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
        // Login and logout are our own JSON endpoints in AuthController.
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives(contentSecurityPolicy(storageProperties)))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                    .permissionsPolicyHeader(
                        permissions ->
                            permissions.policy("camera=(), microphone=(), geolocation=()")));
    return http.build();
  }

  /** Passwords are stored only as salted BCrypt hashes. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Spring Security's standard username-and-password check, against our accounts. */
  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /** The logged-in identity is kept in the server-side HTTP session. */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * Only our own scripts, styles and images are allowed. Frames are allowed only from the project's
   * storage host, for the PDF preview.
   */
  private static String contentSecurityPolicy(StorageProperties storage) {
    String frameSource =
        StringUtils.hasText(storage.supabaseUrl()) && storage.supabaseUrl().startsWith("https://")
            ? storage.supabaseUrl()
            : "'none'";
    return "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
        + "frame-src "
        + frameSource
        + "; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
  }
}
