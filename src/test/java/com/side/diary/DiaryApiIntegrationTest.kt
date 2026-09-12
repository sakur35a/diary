package com.side.diary

import java.nio.charset.StandardCharsets.UTF_8
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 전체 애플리케이션에서 HTTP 직렬화, Location, 예외 응답과 계층 연결을 검증한다. 이 컨텍스트가 뜨는 것 자체가 기동 검사이므로 빈 contextLoads 테스트는
 * 별도로 두지 않는다. MockMvc는 실제 서버 소켓 없이 MVC 요청을 실행하며, 보안 필터와 실제 서비스/저장소는 유지한다.
 *
 * 저장소 테스트와 같은 Spring 설정으로 컨텍스트/DB를 공유하지만, 여기에는 Transactional을 붙이지 않는다. 서비스의 실제 트랜잭션에서 커밋한 뒤 다음 요청으로
 * 조회하기 위해서다. 생성 테스트의 Sql 정리는 assertion이 실패해도 테스트 종료 후 실행되어 다음 테스트에 커밋된 데이터를 남기지 않는다. 외부 OAuth 로그인
 * 왕복과 배포 환경의 네트워크 설정까지 검증하는 테스트는 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DiaryApiIntegrationTest(private val mvc: MockMvc) {
    // ponytail: 현재 테스트는 순차 실행하므로 공유 테스트 DB의 테이블을 비운다.
    // 병렬 실행을 도입하면 테스트별 ID로 정리하거나 스키마를 분리해야 한다.
    // 데이터 정리를 위해 DirtiesContext를 쓰면 컨텍스트와 컨테이너가 다시 만들어져 공유 이점이 사라진다.
    @Sql(
        statements = ["DELETE FROM diaries"],
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
    )
    @Test
    fun `일기를 생성하면 Location 주소에서 같은 일기를 조회할 수 있다`() {
        val created =
            mvc.perform(
                    post("/diary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"제목","content":"내용"}""")
                )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.content").value("내용"))
                .andReturn()
                .response
        val location = assertNotNull(created.getHeader(HttpHeaders.LOCATION))

        mvc.perform(get(location))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.diaryId").value(location.substringAfterLast('/')))
            .andExpect(content().json(created.getContentAsString(UTF_8)))
    }

    // 시나리오와 검증 구조가 같고 데이터만 다른 경우에만 파라미터화한다.
    // 저장 성공/PK 충돌/삭제 상태처럼 준비 과정이 다른 테스트를 억지로 한 표에 넣지 않는다.
    @ParameterizedTest
    @CsvSource("ko, 일기를 찾을 수 없습니다.", "en, Diary not found.", "ja, Diary not found.")
    fun `없는 일기는 요청 언어에 맞는 404를 반환한다`(language: String, detail: String) {
        mvc.perform(
                get("/diary/00000000-0000-7000-8000-000000000000")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, language)
            )
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value(detail))
    }

    @Test
    fun `UUID 형식이 잘못된 경로는 상세 코드가 있는 400을 반환한다`() {
        mvc.perform(get("/diary/not-a-uuid").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_TYPE_MISMATCH"))
            .andExpect(
                jsonPath("$.type")
                    .value("http://localhost:3000/problems/method-argument-type-mismatch")
            )
    }
}
