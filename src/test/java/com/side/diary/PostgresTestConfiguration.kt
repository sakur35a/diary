package com.side.diary

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 테스트용 DB 설정은 이 한곳에서 공유한다. @ServiceConnection이 임의 포트의 접속 정보를 DataSource/Flyway에 제공하므로 URL,
 * 계정, @DynamicPropertySource를 따로 복제할 필요가 없다.
 *
 * 두 DB 테스트는 SpringBootTest/MockMvc/프로필/Import 설정을 같게 맞춘다. 같은 테스트 JVM에서 Spring이 캐시된 컨텍스트를 재사용하므로 이
 * Bean과 PostgreSQL도 한 번만 생성된다. 컨테이너를 직접 전역 singleton으로 만들지 않아도 시작/종료를 Spring에 맡긴 채 공유할 수 있다.
 *
 * 이 설정 파일을 Import하는 것만으로 공유가 보장되는 것은 아니다. 다른 프로필이나 MockitoBean 등으로 컨텍스트 설정이 달라지거나 DirtiesContext로
 * 캐시를 버리면 컨테이너가 추가로 만들어진다. 별도 테스트 JVM 사이에서도 공유되지 않는다. DB 테스트의 ActiveProfiles("test")는 유지해 local의
 * Vault import를 피하고, 데이터 정리는 각 테스트의 롤백/Sql로 처리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18-alpine")
}
