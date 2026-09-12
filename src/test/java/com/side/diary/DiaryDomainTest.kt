package com.side.diary

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

val simpleDiaryCreateRequest = DiaryCreateRequest(title = "simple 제목", content = "간단한 content")

fun simpleDiary() = Diary(title = "simple 제목", content = "간단한 content")

/** 객체 자체의 불변식은 Spring/DB 없이 검사한다. 생성자 계약을 조건과 기대 결과별로 나누고, 이 규칙을 저장소 테스트마다 반복하지 않는다. */
class DiaryDomainTest {
    @Nested
    @DisplayName("Diary 생성자")
    inner class Constructor {
        @Test
        @DisplayName("[UUID v7] ID를 생략하면 생성한다")
        fun `ID를 생략하면 UUID v7을 생성한다`() {
            assertEquals(7, Diary(title = "제목", content = "내용").diaryId.version())
        }

        @Test
        @DisplayName("[IllegalArgumentException] UUID v7이 아닌 ID이면 예외를 던진다")
        fun `UUID v7이 아닌 ID이면 IllegalArgumentException을 던진다`() {
            assertFailsWith<IllegalArgumentException> {
                Diary(diaryId = UUID.randomUUID(), title = "제목", content = "내용")
            }
        }
    }
}
