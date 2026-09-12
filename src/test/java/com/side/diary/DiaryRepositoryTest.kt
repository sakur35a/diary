package com.side.diary

import com.example.jooq.generated.Tables.DIARIES
import com.side.NotFoundException
import com.side.test.PostgresTestConfiguration
import com.side.test.SpringBootIntegrationTest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jooq.DSLContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.annotation.Transactional

/**
 * 이 계층에서는 실제 PostgreSQL의 저장/매핑, 조회 조건, 제약 위반만 검증한다. 서비스의 단순 위임을 같은 DB 시나리오로 다시 검사하지 않고, HTTP 계약은
 * API 테스트에 맡긴다.
 *
 * 실제 PostgreSQL과 전체 애플리케이션 컨텍스트를 사용한다. Repository는 전체 애플리케이션의 컴포넌트 스캔으로 등록되므로 별도 Import도 필요 없다.
 *
 * JooqTest의 자동 롤백을 대체하기 위해 이 클래스에만 Transactional을 명시한다. 각 테스트가 만든 데이터는 종료 시 롤백한다. simpleDiary는 이
 * 파일의 반복 생성만 줄이며 공용 빌더는 만들지 않는다.
 */
@SpringBootIntegrationTest
@Import(PostgresTestConfiguration::class)
@Transactional
class DiaryRepositoryTest(
    private val diaryRepository: DiaryRepository,
    private val dsl: DSLContext,
) {
    @Nested
    @DisplayName("createDiary")
    inner class CreateDiary {
        @Test
        @DisplayName("[Diary] 새 ID의 일기이면 저장하고 반환한다")
        fun `새 ID의 일기이면 저장하고 저장한 일기를 반환한다`() {
            val diary = simpleDiary()

            assertEquals(diary, diaryRepository.createDiary(diary))
            // createDiary의 반환값만 비교하면 실제 INSERT가 빠져도 테스트가 통과할 수 있다.
            // DB 레코드에서 실제 저장값과 도메인에 노출하지 않는 메타데이터를 확인한다.
            val record =
                assertNotNull(
                    dsl.selectFrom(DIARIES).where(DIARIES.DIARY_ID.eq(diary.diaryId)).fetchOne()
                )
            assertEquals(diary.diaryId, record.diaryId)
            assertEquals(diary.title, record.title)
            assertEquals(diary.content, record.content)
            assertNotNull(record.createdAt)
            assertEquals(false, record.deleted)
            assertNull(record.deletedAt)
        }

        @Test
        @DisplayName("[DuplicateKeyException] 이미 존재하는 ID이면 예외를 던진다")
        fun `이미 존재하는 ID이면 DuplicateKeyException을 던진다`() {
            val diary = diaryRepository.createDiary(simpleDiary())

            // 중복 여부를 SELECT한 뒤 INSERT하는 방식은 동시 요청 사이의 경쟁을 막지 못한다.
            // 실제 PK 제약과 Spring의 예외 변환이 작동하는지 DB를 통해 검증한다.
            assertFailsWith<DuplicateKeyException> { diaryRepository.createDiary(diary) }
        }
    }

    @Nested
    @DisplayName("findDiary")
    inner class FindDiary {
        @Test
        @DisplayName("[Diary] 존재하는 ID이면 저장된 일기를 반환한다")
        fun `존재하는 ID이면 저장된 일기를 반환한다`() {
            val diary = diaryRepository.createDiary(simpleDiary())

            assertEquals(diary, diaryRepository.findDiary(diary.diaryId))
        }

        @Test
        @DisplayName("[null] 없는 ID이면 반환한다")
        fun `없는 ID이면 null을 반환한다`() {
            val absentId = UUID.fromString("00000000-0000-7000-8000-000000000000")

            assertNull(diaryRepository.findDiary(absentId))
        }

        @Test
        @DisplayName("[null] 삭제 표시된 일기이면 반환한다")
        fun `삭제 표시된 일기이면 null을 반환한다`() {
            val diary = createDeletedDiary()

            assertNull(diaryRepository.findDiary(diary.diaryId))
        }
    }

    @Nested
    @DisplayName("getDiary")
    inner class GetDiary {
        @Test
        @DisplayName("[Diary] 존재하는 ID이면 저장된 일기를 반환한다")
        fun `존재하는 ID이면 저장된 일기를 반환한다`() {
            val diary = diaryRepository.createDiary(simpleDiary())

            assertEquals(diary, diaryRepository.getDiary(diary.diaryId))
        }

        @Test
        @DisplayName("[NotFoundException] 없는 ID이면 예외를 던진다")
        fun `없는 ID이면 NotFoundException을 던진다`() {
            val absentId = UUID.fromString("00000000-0000-7000-8000-000000000000")

            assertFailsWith<NotFoundException> { diaryRepository.getDiary(absentId) }
        }

        @Test
        @DisplayName("[NotFoundException] 삭제 표시된 일기이면 예외를 던진다")
        fun `삭제 표시된 일기이면 NotFoundException을 던진다`() {
            val diary = createDeletedDiary()

            assertFailsWith<NotFoundException> { diaryRepository.getDiary(diary.diaryId) }
        }
    }

    @Nested
    @DisplayName("modifyDiary")
    inner class ModifyDiary {
        @Test
        @DisplayName("[Diary] 일기를 수정하고 반환한다")
        fun `일기를 수정하고 반환한다`() {
            var diary = simpleDiary()
            diaryRepository.createDiary(diary)

            diary = diary.copy(title = diary.title.padEnd(255, 'x'))

            assertEquals(diary, diaryRepository.modifyDiary(diary))
            val record =
                assertNotNull(
                    dsl.selectFrom(DIARIES).where(DIARIES.DIARY_ID.eq(diary.diaryId)).fetchOne()
                )
            assertEquals(diary.diaryId, record.diaryId)
            assertEquals(diary.title, record.title)
            assertEquals(diary.content, record.content)
            assertNotNull(record.createdAt)
            assertEquals(false, record.deleted)
            assertNull(record.deletedAt)
        }

        @Test
        @DisplayName("[NotFoundException] 없는 ID이면 예외를 던진다")
        fun `없는 ID이면 예외를 던진다`() {
            assertFailsWith<NotFoundException> {
                diaryRepository.modifyDiary(
                    Diary(
                        diaryId = UUID.fromString("00000000-0000-7000-8000-000000000000"),
                        title = "제목",
                        content = "내용",
                    )
                )
            }
        }

        @Test
        @DisplayName("[NotFoundException] 삭제 표시된 ID이면 예외를 던진다")
        fun `삭제 표시된 ID이면 예외를 던진다`() {
            val diary = createDeletedDiary()

            assertFailsWith<NotFoundException> { diaryRepository.modifyDiary(diary) }
        }
    }

    private fun createDeletedDiary(): Diary {
        val diary = diaryRepository.createDiary(simpleDiary())
        // 삭제 API가 없으므로 두 조회 테스트에서 사용할 삭제 상태를 SQL로 준비한다.
        dsl.update(DIARIES)
            .set(DIARIES.DELETED, true)
            .where(DIARIES.DIARY_ID.eq(diary.diaryId))
            .execute()
        return diary
    }
}
