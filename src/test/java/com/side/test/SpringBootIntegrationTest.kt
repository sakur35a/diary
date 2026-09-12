package com.side.test

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor

/** 전체 Spring Boot 컨텍스트와 생성자 주입을 사용하는 통합 테스트의 공통 설정. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
annotation class SpringBootIntegrationTest
