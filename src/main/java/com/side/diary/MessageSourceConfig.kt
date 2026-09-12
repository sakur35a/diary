package com.side.diary

import org.springframework.boot.autoconfigure.context.MessageSourceProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessageSourceProperties::class)
class MessageSourceConfig {
    // 외부 파일의 수정 시각을 확인해 재로딩한다. classpath 리소스의 재로딩은 보장되지 않는다.
    // https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/support/ReloadableResourceBundleMessageSource.html
    @Bean
    fun messageSource(properties: MessageSourceProperties): ReloadableResourceBundleMessageSource =
        ReloadableResourceBundleMessageSource().apply {
            setBasenames(*properties.basename.toTypedArray())
            setDefaultEncoding(properties.encoding.name())
            setFallbackToSystemLocale(properties.isFallbackToSystemLocale)
            setCacheMillis(properties.cacheDuration?.toMillis() ?: -1)
        }
}
