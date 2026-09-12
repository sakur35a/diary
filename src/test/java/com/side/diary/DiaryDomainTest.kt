package com.side.diary

import com.side.diary.diary.Diary
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DiaryDomainTest {
    @Nested
    inner class `성공` {
        @Test
        fun `생성한 일기 ID는 UUID v7이다`() {
            assertEquals(7, Diary(title = "제목", content = "내용").diaryId.version())
        }
    }

    @Nested
    inner class `실패` {

        @Test
        fun `UUID v7이 아닌 ID는 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                Diary(diaryId = UUID.randomUUID(), title = "제목", content = "내용")
            }
        }
    }
}
