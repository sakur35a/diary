package com.side.diary

import java.nio.charset.StandardCharsets
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource

@Configuration(proxyBeanMethods = false)
class MessageSourceConfig {

  @Bean
  fun messageSource(): MessageSource =
      ReloadableResourceBundleMessageSource().apply {
        setBasenames("classpath:messages")
        setDefaultEncoding(StandardCharsets.UTF_8.name())
        setCacheMillis(1000) // 1초마다 파일 변경 확인
        setFallbackToSystemLocale(false)
      }
}
