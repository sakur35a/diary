package com.side.diary

import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.epages.restdocs.apispec.Schema
import com.side.test.AutoConfigureMockMvcRestDocs
import com.side.test.PostgresTestConfiguration
import com.side.test.SpringBootIntegrationTest
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/**
 * 전체 애플리케이션에서 HTTP 직렬화, Location, 예외 응답과 계층 연결을 검증한다. 이 컨텍스트가 뜨는 것 자체가 기동 검사이므로 빈 contextLoads 테스트는
 * 별도로 두지 않는다. MockMvc는 실제 서버 소켓 없이 MVC 요청을 실행하며, 보안 필터와 실제 서비스/저장소는 유지한다.
 *
 * 여기에는 Transactional을 붙이지 않는다. 서비스의 실제 트랜잭션에서 커밋한 뒤 다음 요청으로 조회하기 위해서다. 각 테스트의 Sql 정리는 assertion이
 * 실패해도 테스트 종료 후 실행되어 다음 테스트에 커밋된 데이터를 남기지 않는다. 외부 OAuth 로그인 왕복과 배포 환경의 네트워크 설정까지 검증하는 테스트는 아니다.
 */
@SpringBootIntegrationTest
@AutoConfigureMockMvcRestDocs
@Import(PostgresTestConfiguration::class)
// ponytail: 현재 테스트는 순차 실행하므로 공유 테스트 DB의 테이블을 비운다.
// 병렬 실행을 도입하면 테스트별 ID로 정리하거나 스키마를 분리해야 한다.
// 데이터 정리를 위해 DirtiesContext를 쓰면 컨텍스트와 컨테이너가 다시 만들어져 공유 이점이 사라진다.
@Sql(
    statements = ["DELETE FROM diaries"],
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
)
class DiaryApiIntegrationTest(
    private val mvc: MockMvc,
    private val diaryRepository: DiaryRepository,
    private val objectMapper: ObjectMapper,
) {
    private val diaryFields =
        listOf(
            fieldWithPath("diaryId").description("서버에서 생성한 UUID v7 일기 ID"),
            fieldWithPath("title").description("일기 제목"),
            fieldWithPath("content").description("일기 내용"),
        )
    private val problemFields =
        listOf(
            fieldWithPath("type")
                .type(JsonFieldType.STRING)
                .description("문제 유형 URI (생략 시 about:blank)")
                .optional(),
            fieldWithPath("title").description("HTTP 오류 제목"),
            fieldWithPath("status").description("HTTP 상태 코드"),
            fieldWithPath("detail").description("요청 언어에 맞춘 오류 설명"),
            fieldWithPath("instance").description("오류가 발생한 요청 경로"),
        )

    @Nested
    @DisplayName("POST /diary")
    inner class PostDiary {
        @Test
        fun `제목과 내용을 보내면 201과 생성된 일기 및 Location을 반환한다`() {
            val title = "제목-${UUID.randomUUID()}"
            val content = "내용-${UUID.randomUUID()}"
            val created =
                mvc.perform(
                        post("/diary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title":"$title","content":"$content"}""")
                    )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.title").value(title))
                    .andExpect(jsonPath("$.content").value(content))
                    // andDo는 assertion(andExpect)과 달리 요청 결과를 받아 부가 작업을 실행한다.
                    // document(...)는 ResultHandler를 반환하므로 여기서 REST Docs 스니펫을 만든다.
                    // 같은 요청에 andDo를 여러 번 연결할 수 있고, 각 핸들러는 앞의 결과를 그대로 다음 단계로 넘긴다.
                    .andDo(
                        document(
                            "diary-create",
                            // 실제 요청은 검증한 값을 사용하고, 문서의 요청 예제만 Scalar 동적 변수로 바꾼다.
                            preprocessRequest(
                                replacePattern(
                                    Pattern.compile(Pattern.quote(title)),
                                    "제목-{{\$randomUUID}}",
                                ),
                                replacePattern(
                                    Pattern.compile(Pattern.quote(content)),
                                    "내용-{{\$randomUUID}}",
                                ),
                            ),
                            resource(
                                ResourceSnippetParameters.builder()
                                    .tag("Diary")
                                    .summary("일기 생성")
                                    .requestSchema(Schema.schema("DiaryCreateRequest"))
                                    .requestFields(
                                        fieldWithPath("title").description("일기 제목"),
                                        fieldWithPath("content").description("일기 내용"),
                                    )
                                    .responseSchema(Schema.schema("Diary"))
                                    .responseFields(diaryFields)
                                    .responseHeaders(
                                        headerWithName(HttpHeaders.LOCATION)
                                            .description("생성된 일기 조회 경로")
                                    )
                                    .build()
                            ),
                        )
                    )
                    .andReturn()
            val location = assertNotNull(created.response.getHeader(HttpHeaders.LOCATION))
            val diaryId = UUID.fromString(location.substringAfterLast('/'))
            assertEquals("/diary/$diaryId", location)
            // Location이 가리키는 ID와 응답 본문의 ID가 일치해야 한다.
            jsonPath("$.diaryId").value(diaryId.toString()).match(created)
        }

        @Test
        fun `제목이 255자를 넘으면 400을 반환한다`() {
            val title = "제목".padEnd(256, 'X')
            val content = "내용-${UUID.randomUUID()}"
            mvc.perform(
                    post("/diary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(DiaryCreateRequest(title, content))
                        )
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_NOT_VALID"))
                .andExpect(
                    jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("title:"))
                )
                .andDo(
                    document(
                        "diary-create-failed-by-long-title",
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("Diary")
                                .summary("일기 생성")
                                .requestSchema(Schema.schema("DiaryCreateRequest"))
                                .requestFields(
                                    fieldWithPath("title").description("일기 제목"),
                                    fieldWithPath("content").description("일기 내용"),
                                )
                                .responseSchema(Schema.schema("InvalidParameterProblem"))
                                .responseFields(
                                    problemFields + fieldWithPath("code").description("상세 오류 코드")
                                )
                                .build()
                        ),
                    )
                )
                .andReturn()
        }

        @Test
        fun `content가 공백이면 400을 반환한다`() {
            val title = "제목-${UUID.randomUUID()}"
            val content = " "
            mvc.perform(
                    post("/diary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(DiaryCreateRequest(title, content))
                        )
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_NOT_VALID"))
                .andExpect(
                    jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("content:"))
                )
                .andDo(
                    document(
                        "diary-create-failed-by-blank-content",
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("Diary")
                                .summary("일기 생성")
                                .requestSchema(Schema.schema("DiaryCreateRequest"))
                                .requestFields(
                                    fieldWithPath("title").description("일기 제목"),
                                    fieldWithPath("content").description("일기 내용"),
                                )
                                .responseSchema(Schema.schema("InvalidParameterProblem"))
                                .responseFields(
                                    problemFields + fieldWithPath("code").description("상세 오류 코드")
                                )
                                .build()
                        ),
                    )
                )
                .andReturn()
        }
    }

    @Nested
    @DisplayName("GET /diary/{diaryId}")
    inner class GetDiary {
        @Test
        fun `존재하는 ID이면 200과 저장된 일기를 반환한다`() {
            // 조회 계약을 독립적으로 검증할 수 있도록 Repository로 데이터를 준비한다.
            val diary = diaryRepository.createDiary(Diary(title = "조회할 제목", content = "조회할 내용"))

            mvc.perform(get("/diary/{diaryId}", diary.diaryId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.diaryId").value(diary.diaryId.toString()))
                .andExpect(jsonPath("$.title").value(diary.title))
                .andExpect(jsonPath("$.content").value(diary.content))
                // 이 andDo도 조회 요청의 결과를 문서화한다. 문서화는 검증이 아니므로 필요한 상태 검증은 andExpect로 별도로 작성한다.
                .andDo(
                    document(
                        "diary-get",
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("Diary")
                                .summary("일기 조회")
                                .pathParameters(
                                    parameterWithName("diaryId").description("조회할 일기 UUID")
                                )
                                .responseSchema(Schema.schema("Diary"))
                                .responseFields(diaryFields)
                                .build()
                        ),
                    )
                )
        }

        // 시나리오와 검증 구조가 같고 데이터만 다른 경우에만 파라미터화한다.
        // 저장 성공/PK 충돌/삭제 상태처럼 준비 과정이 다른 테스트를 억지로 한 표에 넣지 않는다.
        @ParameterizedTest
        @CsvSource("ko, 일기를 찾을 수 없습니다.", "en, Diary not found.", "ja, Diary not found.")
        fun `없는 ID이면 요청 언어에 맞는 404를 반환한다`(language: String, detail: String) {
            mvc.perform(
                    get("/diary/{diaryId}", "00000000-0000-7000-8000-000000000000")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                )
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(detail))
                // 파라미터 테스트에서는 language별로 다른 snippet 이름을 사용해 하나의 OpenAPI 문서에 예제를 모두 남긴다.
                .andDo(
                    document(
                        "diary-get-not-found-$language",
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("Diary")
                                .summary("일기 조회")
                                .pathParameters(
                                    parameterWithName("diaryId").description("조회할 일기 UUID")
                                )
                                .requestHeaders(
                                    headerWithName(HttpHeaders.ACCEPT_LANGUAGE)
                                        .description("오류 설명 언어: ko는 한국어, en 및 미지원 언어는 영어")
                                        .optional()
                                )
                                .responseSchema(Schema.schema("ProblemDetail"))
                                .responseFields(problemFields)
                                .build()
                        ),
                    )
                )
        }

        @Test
        fun `UUID 형식이 잘못되면 상세 코드가 있는 400을 반환한다`() {
            mvc.perform(
                    get("/diary/{diaryId}", "not-a-uuid").header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_TYPE_MISMATCH"))
                .andExpect(
                    jsonPath("$.type")
                        .value("http://localhost:5173/problems/method-argument-type-mismatch")
                )
                // 요청/응답을 파일로 기록하는 부가 동작이다. assertion이 실패하면 여기까지 도달하지 않아 문서도 생성되지 않는다.
                .andDo(
                    document(
                        "diary-get-invalid-id",
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("Diary")
                                .summary("일기 조회")
                                .pathParameters(
                                    parameterWithName("diaryId").description("조회할 일기 UUID")
                                )
                                .requestHeaders(
                                    headerWithName(HttpHeaders.ACCEPT_LANGUAGE)
                                        .description("오류 설명 언어: ko는 한국어, en 및 미지원 언어는 영어")
                                        .optional()
                                )
                                .responseSchema(Schema.schema("InvalidParameterProblem"))
                                .responseFields(
                                    problemFields + fieldWithPath("code").description("상세 오류 코드")
                                )
                                .build()
                        ),
                    )
                )
        }
    }

    @Nested
    @DisplayName("생성 후 조회 시나리오")
    inner class CreateAndGetDiary {
        @Test
        fun `일기를 생성하면 Location 주소에서 같은 일기를 조회할 수 있다`() {
            val created =
                mvc.perform(
                        post("/diary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(simpleDiaryCreateRequest))
                    )
                    .andExpect(status().isCreated)
                    .andReturn()
                    .response
            val location = assertNotNull(created.getHeader(HttpHeaders.LOCATION))

            // 개별 응답 계약은 엔드포인트별 테스트에서, 여기서는 두 요청의 연결을 검증한다.
            mvc.perform(get(location))
                .andExpect(status().isOk)
                .andExpect(content().json(created.getContentAsString(UTF_8)))
        }
    }
}

/*
 * MockMvc 요청 체인의 주요 메서드
 *
 * - andExpect(...): 상태 코드, 헤더, JSON 경로처럼 테스트가 반드시 만족해야 하는 조건을 검증한다. 실패하면 테스트가 실패한다.
 * - andDo(...): 검증 후 ResultHandler를 실행한다. document(...)는 REST Docs 파일을 생성하고, print()는 콘솔에 요청/응답을 출력한다.
 * - andReturn(): 체인을 끝내고 MvcResult를 반환한다. Location이나 응답 본문처럼 테스트 코드에서 직접 읽을 때 사용한다.
 * - andExpectAll(...): 여러 ResultMatcher를 한 번에 등록한다. 각 matcher의 오류를 모아 보여주고 싶을 때 사용할 수 있다.
 * - andExpect { result -> ... }: 기본 matcher로 표현하기 어려운 조건을 람다에서 직접 검증한다. 실패시키려면 assertion을 호출한다.
 * - andDo { result -> ... }: MvcResult를 직접 다루는 사용자 정의 후처리다. 로그, 디버깅 정보 저장처럼 문서 생성 외 작업에 사용한다.
 * - alwaysExpect(...): 모든 MockMvc 요청에 공통 검증을 등록한다. 보통 MockMvc 설정에서 사용하며 테스트별 조건은 andExpect로 둔다.
 * - alwaysDo(...): 모든 요청에 공통 ResultHandler를 등록한다. 실패 시에만 출력하는 `print()` 같은 전역 디버깅 설정에 유용하다.
 *
 * 요청을 만들 때는 get/post/put/patch/delete 같은 RequestBuilder를 사용하고, 다음 메서드로 요청을 채운다.
 * - `.param("name", "value")`: query parameter를 추가한다.
 * - `.header("X-Name", "value")` / `.headers(...)`: 요청 헤더를 추가한다.
 * - `.contentType(...)`: 요청 본문의 Media Type을 지정한다.
 * - `.content(...)`: JSON·문자열·바이너리 요청 본문을 넣는다.
 * - `.accept(...)`: 클라이언트가 원하는 응답 Media Type을 지정한다.
 *
 * 예시:
 *
 * mvc.perform(get("/diary/{diaryId}", diaryId))
 *     .andExpect(status().isOk())
 *     .andDo(print())
 *     .andReturn()
 *
 * document(...)는 andExpect(...)의 대체재가 아니다. API 계약을 확인하는 assertion과 문서 파일을 생성하는 ResultHandler를 함께 사용해야 테스트가
 * 실제 동작도 검증하고 최신 문서도 남긴다. 모든 요청에 로그를 남기고 싶다면 MockMvc 설정의 alwaysDo(print())를 사용할 수 있지만, 현재 테스트처럼
 * OpenAPI에 포함할 요청만 document(...)를 명시적으로 연결하는 방식이 문서 범위를 제어하기 쉽다.
 */
