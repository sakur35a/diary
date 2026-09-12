package com.side.diary.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    @Value("\${app.frontend.oauth-callback-url:http://localhost:3000/oauth/callback}")
    private val callbackUrl: String
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        response.sendRedirect(callbackUrl)
    }
}

@Component
class OAuth2LoginFailureHandler : AuthenticationFailureHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: org.springframework.security.core.AuthenticationException,
    ) {
        if (exception is OAuth2AuthenticationException) {
            logger.warn("OAuth2 login failed: errorCode={}", exception.error.errorCode)
        } else {
            logger.warn("OAuth2 login failed", exception)
        }

        val error = URLEncoder.encode("login_failed", StandardCharsets.UTF_8)
        response.sendRedirect("http://localhost:3000/oauth/callback?error=$error")
    }
}
