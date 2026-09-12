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
        // TODO(접근 제어): 현재 anyRequest().permitAll()이라 로그인 없이 일기 생성/조회가 가능하다.
        // oauth2Login은 로그인 기능을 구성할 뿐 일기의 접근 권한을 자동으로 제한하지 않는다.
        // 개인 일기 서비스라면 인증 필수화와 함께 owner ID를 저장하고 조회 SQL에도 소유자 조건을
        // 넣어야 한다. 로그인 여부만 검사하면 다른 사용자의 ID를 통한 조회를 막을 수 없다.
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
