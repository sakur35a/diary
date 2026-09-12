package com.side.diary

import com.side.diary.diary.DiaryCreateRequest
import com.side.diary.diary.DiaryRepository
import com.side.diary.diary.DiaryService
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.jooq.test.autoconfigure.JooqTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor

@JooqTest
@Import(
    DiaryService::class,
    DiaryRepository::class,
    PostgresTestConfiguration::class,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DiaryServiceIntegrationTest(private val diaryService: DiaryService) {
    @Nested
    inner class 성공 {

        @Test
        fun `조회하기`() {
            val request =
                DiaryCreateRequest(
                    title = "제목",
                    content = "내용",
                )

            val diary = diaryService.createDiary(request.toDiary())

            val result = diaryService.getDiary(diary.diaryId)

            assertEquals(diary, result)
        }
    }

    @Nested
    inner class 실패 {
        @Test
        fun `존재하지 않는 일기를 조회하면 notfoundexception이 발생한다`() {
            val absentId = UUID.fromString("00000000-0000-4000-8000-000000000000")

            assertThrows<NotFoundException> { diaryService.getDiary(absentId) }
        }
    }
}
