package com.side.diary

import com.side.diary.diary.Diary
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * 객체 자체의 불변식은 Spring/DB 없이 검사한다. 이 규칙을 저장소 테스트마다 반복하지 않는다. 성공/실패마다 테스트가 하나뿐이므로 @Nested 계층보다 행위를
 * 설명하는 이름만으로 충분하다. 공통 준비나 여러 관련 사례를 묶을 때 @Nested를 도입하면 된다.
 */
class DiaryDomainTest {
    @Test
    fun `생성한 일기 ID는 UUID v7이다`() {
        assertEquals(7, Diary(title = "제목", content = "내용").diaryId.version())
    }

    @Test
    fun `UUID v7이 아닌 ID는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            Diary(diaryId = UUID.randomUUID(), title = "제목", content = "내용")
        }
    }
}
