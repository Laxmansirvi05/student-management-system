package com.campuscompanion.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Interim security configuration for tasks T1-02 to T1-05: deny by default, with only the health
 * endpoint public. Login, sessions, CSRF handling for the frontend, security headers and the URL
 * rules for each role are added in task T1-06.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        requests ->
            requests.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated());
    return http.build();
  }

  /**
   * No account can log in yet. Declaring this bean stops Spring Boot from creating a default user
   * with a generated password. Task T1-06 replaces it with the database-backed service.
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return username -> {
      throw new UsernameNotFoundException("Login is not available yet");
    };
  }
}
