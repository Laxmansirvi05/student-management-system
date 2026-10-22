package com.campuscompanion.common.security;

import com.campuscompanion.auth.PasswordChangeRequiredFilter;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.common.config.StorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.util.StringUtils;

/**
 * Security for the whole application: deny by default, server-side sessions, CSRF protection and
 * security headers. The rules come from docs/10-authorization-matrix.md and
 * docs/11-security-plan.md. URL rules are the first check; admin services and controllers repeat
 * the role check with @PreAuthorize, so a wrongly mapped URL cannot expose them.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** API groups that hold a student's own records. An admin has no use for them. */
  private static final String[] STUDENT_ONLY_API = {
    "/api/v1/students/**",
    "/api/v1/attendance/**",
    "/api/v1/marks/**",
    "/api/v1/results/**",
    "/api/v1/timetable/**",
    "/api/v1/bookmarks/**",
    "/api/v1/pyqs/**",
    "/api/v1/complaints/**",
    "/api/v1/notes/**",
    "/api/v1/quiz-attempts/**",
    "/api/v1/progress/**",
    "/api/v1/ai/**",
    "/api/v1/assistant/**"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiSecurityErrorHandler errorHandler,
      SecurityContextRepository securityContextRepository,
      SessionRegistry sessionRegistry,
      UserRepository users,
      StorageProperties storageProperties,
      @Value("${app.security.require-https}") boolean requireHttps)
      throws Exception {
    if (requireHttps) {
      // Production only: a plain HTTP request is sent to the same address over HTTPS.
      http.redirectToHttps(Customizer.withDefaults());
    }
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/login.html", "/css/**", "/js/**", "/favicon.ico")
                    .permitAll()
                    // The privacy policy and the terms can be read before logging in. They are
                    // plain pages with no script, and show nothing about any person.
                    .requestMatchers("/privacy.html", "/terms.html")
                    .permitAll()
                    // The landing page, and the icon file it shares with every other page. Neither
                    // shows anything about any person; a logged-in user is sent on to their home.
                    .requestMatchers("/", "/index.html", "/img/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**", "/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(STUDENT_ONLY_API)
                    .hasRole("STUDENT")
                    .requestMatchers("/student/**")
                    .hasRole("STUDENT")
                    .anyRequest()
                    .authenticated())
        // The CSRF token travels in the XSRF-TOKEN cookie and comes back in the X-XSRF-TOKEN
        // header. It is required on every state-changing request, including login.
        .csrf(csrf -> csrf.spa())
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        // Sessions are registered so the server can end them: the other sessions of a user after
        // a password change, all of them after a reset or a deactivation. No limit on how many.
        .sessionManagement(
            sessions ->
                sessions.sessionConcurrency(
                    concurrency ->
                        concurrency
                            .maximumSessions(-1)
                            .sessionRegistry(sessionRegistry)
                            .expiredSessionStrategy(
                                event ->
                                    errorHandler.commence(
                                        event.getRequest(), event.getResponse(), null))))
        // While a password change is required, only the requests needed to change it pass.
        .addFilterBefore(
            new PasswordChangeRequiredFilter(users, errorHandler), AuthorizationFilter.class)
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
                    // Sent on HTTPS responses only, as browsers ignore it on plain HTTP.
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
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

  /** Spring Security's record of which sessions belong to which account. */
  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  /** Tells the session registry when a session ends or changes its identifier. */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  /** The logged-in identity is kept in the server-side HTTP session. */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * Only our own scripts and styles are allowed. Besides our own origin, two things may come from
   * the project's storage host and from nowhere else: a frame, for the PDF preview, and an image,
   * for a profile photo. Both are reached through short-lived signed links.
   */
  static String contentSecurityPolicy(StorageProperties storage) {
    String url = storage.supabaseUrl();
    // A placeholder or malformed value must never be written into the header.
    boolean configured = StringUtils.hasText(url) && url.matches("^https://[a-z0-9.-]+$");
    // 'wasm-unsafe-eval' lets the robot's 3D scene compile its WebAssembly mesh decoder; it does
    // not
    // allow eval() of text. The decoder runs in a worker made from a blob, hence worker-src.
    return "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:;"
        + " style-src 'self'; img-src 'self' data:"
        + (configured ? " " + url : "")
        + "; frame-src "
        + (configured ? url : "'none'")
        + "; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
  }
}
