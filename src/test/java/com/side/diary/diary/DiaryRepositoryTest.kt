package com.side.diary.diary

import com.example.jooq.generated.Tables.DIARIES
import com.side.diary.NotFoundException
import com.side.diary.PostgresTestConfiguration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional

private fun simpleDiary() = Diary(title = "simple 제목", content = "간단한 content")

/**
 * 이 계층에서는 실제 PostgreSQL의 저장/매핑, 조회 조건, 제약 위반만 검증한다. 서비스의 단순 위임을 같은 DB 시나리오로 다시 검사하지 않고, HTTP 계약은
 * API 테스트에 맡긴다.
 *
 * 실제 PostgreSQL과 전체 애플리케이션 컨텍스트를 사용한다. Repository는 전체 애플리케이션의 컴포넌트 스캔으로 등록되므로 별도 Import도 필요 없다.
 *
 * JooqTest의 자동 롤백을 대체하기 위해 이 클래스에만 Transactional을 명시한다. 각 테스트가 만든 데이터는 종료 시 롤백한다. simpleDiary는 이
 * 파일의 반복 생성만 줄이며 공용 빌더는 만들지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class DiaryRepositoryTest(
    private val diaryRepository: DiaryRepository,
    private val dsl: DSLContext,
) {
    @Test
    fun `저장한 일기는 두 조회 방식에서 같은 값으로 복원된다`() {
        val diary = simpleDiary()

        assertEquals(diary, diaryRepository.createDiary(diary))
        // createDiary의 반환값만 비교하면 실제 INSERT가 빠져도 테스트가 통과할 수 있다.
        // 다시 조회해 영속화와 jOOQ의 Kotlin 객체 매핑까지 확인한다.
        assertEquals(diary, diaryRepository.getDiary(diary.diaryId))
        assertEquals(diary, diaryRepository.findDiary(diary.diaryId))

        // 도메인에 노출하지 않는 저장 메타데이터는 DB 레코드에서 확인한다.
        val record =
            assertNotNull(
                dsl.selectFrom(DIARIES).where(DIARIES.DIARY_ID.eq(diary.diaryId)).fetchOne()
            )
        assertNotNull(record.createdAt)
        assertEquals(false, record.deleted)
        assertNull(record.deletedAt)
    }

    @Test
    fun `없는 일기는 find에서 null이고 get에서 예외다`() {
        val absentId = UUID.fromString("00000000-0000-7000-8000-000000000000")

        // 존재하지 않는 행을 확인하는 데 다른 일기를 미리 INSERT할 필요는 없다.
        assertNull(diaryRepository.findDiary(absentId))
        assertFailsWith<NotFoundException> { diaryRepository.getDiary(absentId) }
    }

    @Test
    fun `삭제 표시된 일기는 두 조회 방식 모두에서 숨긴다`() {
        val diary = diaryRepository.createDiary(simpleDiary())
        // 삭제 API는 아직 없으므로 테스트에서만 SQL로 상태를 준비한다.
        // 테스트 편의를 위한 삭제 메서드를 운영 Repository에 추가하지 않는다.
        dsl.update(DIARIES)
            .set(DIARIES.DELETED, true)
            .where(DIARIES.DIARY_ID.eq(diary.diaryId))
            .execute()

        assertNull(diaryRepository.findDiary(diary.diaryId))
        assertFailsWith<NotFoundException> { diaryRepository.getDiary(diary.diaryId) }
    }

    @Test
    fun `이미 존재하는 ID로 저장하면 DB가 중복을 거부한다`() {
        val diary = diaryRepository.createDiary(simpleDiary())

        // 중복 여부를 SELECT한 뒤 INSERT하는 방식은 동시 요청 사이의 경쟁을 막지 못한다.
        // 실제 PK 제약과 Spring의 예외 변환이 작동하는지 DB를 통해 검증한다.
        assertFailsWith<DuplicateKeyException> { diaryRepository.createDiary(diary) }
    }
}
