package com.side.diary.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val loginSuccessHandler: OAuth2LoginSuccessHandler,
    private val loginFailureHandler: OAuth2LoginFailureHandler,
) {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .csrf { it.disable() }
        .authorizeHttpRequests { authorize ->
          authorize
              .requestMatchers("/oauth2/**", "/login/**", "/error")
              .permitAll()
              .anyRequest()
              .permitAll()
        }
        .oauth2Login { oauth2 ->
          oauth2.successHandler(loginSuccessHandler).failureHandler(loginFailureHandler)
        }

    return http.build()
  }
}
