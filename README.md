# Diary API 문서

`DiaryApiIntegrationTest`의 실제 요청/응답을 Spring REST Docs로 검증하고,
`restdocs-api-spec`이 OpenAPI 3 스펙을 생성한다. Scalar는 이 스펙을 읽어 화면에 표시한다.

```sh
# 테스트 및 OpenAPI 생성 (Testcontainers용 Docker 호환 런타임 필요)
./gradlew openapi3

# 스펙 생성 후 애플리케이션 실행 (기존 local DB/Vault 설정 필요)
./gradlew bootRun

# 테스트와 스펙 생성을 포함한 실행 JAR 빌드
./gradlew bootJar
```

- 문서 UI: `http://localhost:8080/docs/index.html`
- OpenAPI: `http://localhost:8080/docs/openapi3.yaml`
- 생성 파일: `build/api-spec/static/docs/openapi3.yaml`
- IntelliJ 직접 실행용 복사본: `src/main/resources/static/docs/openapi3.yaml`
- 범위: `POST /diary` 201, `GET /diary/{diaryId}` 200/400/404. 404는 ko/en/ja 예제를 포함한다.

테스트 이름은 계층의 역할에 맞춰 읽는다. API 통합 테스트는 HTTP 계약을 검증하므로 엔드포인트 아래에 기대 상태 코드를 먼저 표시한다.
Repository·도메인 테스트는 HTTP 상태가 없으므로 반환값이나 예외 타입을 접두사로 사용한다.

```text
GET /diary/{diaryId}
├── [200] 존재하는 ID이면 저장된 일기를 반환한다
├── [400] UUID 형식이 잘못되면 상세 오류 코드를 반환한다
└── [404] 없는 ID이면 요청 언어에 맞는 오류 응답을 반환한다

getDiary
├── [Diary] 존재하는 ID이면 저장된 일기를 반환한다
├── [NotFoundException] 없는 ID이면 예외를 던진다
└── [NotFoundException] 삭제 표시된 일기이면 예외를 던진다
```

`@Nested`는 성공·실패를 최상위로 나누는 용도가 아니라, 같은 행위나 엔드포인트의 사례를 묶고 공통 준비를 공유하는 용도로 사용한다.
테스트 이름에는 조건과 기대 결과를 함께 적어 테스트 목록만 읽어도 검증 범위를 알 수 있게 한다. JUnit의 `@DisplayName`은 실행기와 IDE에 표시되는 이름을 지정하고,
Kotlin의 백틱 메서드명은 자연어 테스트 이름을 작성하는 데 사용한다.

참고:

- [JUnit User Guide: Nested Tests](https://docs.junit.org/current/writing-tests/nested-tests.html)
- [JUnit User Guide: Display Names](https://docs.junit.org/current/writing-tests/display-names.html)
- [Google Testing Blog: Writing Descriptive Test Names](https://testing.googleblog.com/2014/10/testing-on-toilet-writing-descriptive.html)
- [Google Testing Blog: Test Failures Should Be Actionable](https://testing.googleblog.com/2024/05/test-failures-should-be-actionable.html)

`bootRun`과 `bootJar`는 생성한 스펙을 정적 리소스로 제공한다. IDE에서 main을 직접 실행할 때는
`openapi3`로 복사본을 생성한 뒤 리소스를 다시 빌드하고 애플리케이션을 실행한다.
Scalar 스크립트는 `src/main/resources/static/docs/scalar-api-reference.js`에 포함되어 있다.
현재 보안 설정에서는 문서도 익명 접근 가능하다.

요청 예제에 `{{$randomUUID}}`를 넣으면 POST·PUT·PATCH의 전송마다 새 UUID가 생성된다.
공통 `onBeforeRequest` 훅이 모든 경로의 raw 본문(JSON 포함)과 폼 문자열에 적용된다.
중첩 JSON과 배열 안의 변수도 치환되며 업로드 파일은 그대로 전송한다.
로컬 Scalar 1.68.0의 기본 UUID 변수는 고정된 15개 샘플을 재사용하므로,
이 변수만 `crypto.getRandomValues`로 UUID v4를 생성해 치환한다. HTTPS가 아닌 내부 HTTP에서도 동작한다.

```json
{
  "title": "제목-{{$randomUUID}}",
  "content": "내용-{{$randomUUID}}"
}
```

중복되지 않는 이메일 예제가 필요하면 `user-{{$randomUUID}}@example.com`처럼 조합한다.
`{{$randomEmail}}`, `{{$isoTimestamp}}` 등 다른 변수는 Scalar 기본 기능을 사용하며 고유성을 보장하지 않는다.
편집기에는 변수 표현식이 유지되고 실제 전송 값이 매번 바뀐다. 새로고침 후 다시 전송해도 새 값이 생성된다.
고정값을 직접 입력하면 그대로 전송하므로 수정 대상 ID, enum, 인증 값 등은 필요 없이 랜덤화하지 않는다.
변수가 없는 필드는 자동으로 바뀌지 않으며, 랜덤 값만으로 모든 재요청 실패를 방지하지는 못한다.
현재 일기 테스트는 실제 title/content를 검증하고 REST Docs 요청 전처리에서만 동적 변수로 치환한다.
새 API도 랜덤 값이 필요한 요청 예제에 같은 방식으로 변수를 넣으면 된다.

공통 훅 검증: `node --test src/test/js/scalar-request.test.cjs`

새 API는 테스트에서 `RestDocumentationRequestBuilders`의 URI 템플릿과
`document(..., resource(...))`를 추가한다. 필드 설명이 실제 본문과 맞지 않으면 테스트가 실패한다.
실행 시 문서 조각 디렉터리를 비우므로 전체 스펙 생성에는 테스트 필터 없이 `openapi3`를 사용한다.

공식 문서:

- [restdocs-api-spec: Boot 호환표, MockMvc 문서화, Gradle 설정](https://github.com/ePages-de/restdocs-api-spec)
- [Spring Boot: AutoConfigureRestDocs](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/restdocs/test/autoconfigure/AutoConfigureRestDocs.html)
- [Scalar: HTML/JS 연동](https://scalar.com/products/api-references/integrations/html-js)
- [Scalar: 동적 변수](https://scalar.com/products/api-client/dynamic-variables)
