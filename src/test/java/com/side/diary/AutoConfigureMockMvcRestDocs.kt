package com.side.diary

import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc

/** MockMvc 요청 실행과 REST Docs 스니펫 생성을 함께 활성화하는 테스트용 합성 annotation. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@AutoConfigureMockMvc
@AutoConfigureRestDocs
annotation class AutoConfigureMockMvcRestDocs
