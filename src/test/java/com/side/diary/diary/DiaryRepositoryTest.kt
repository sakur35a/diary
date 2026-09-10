package com.side.diary.diary

import com.side.diary.NotFoundException
import com.side.diary.PostgresTestConfiguration
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.jooq.test.autoconfigure.JooqTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.TestConstructor

private fun simpleDiary() = Diary(title = "simple 제목", content = "간단한 content")

@JooqTest
@Import(
    DiaryRepository::class,
    PostgresTestConfiguration::class,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DiaryRepositoryTest(private val diaryRepository: DiaryRepository) {

  val absentId: UUID = UUID.fromString("00000000-0000-4000-8000-000000000000")

  @Nested
  inner class 성공 {

    @Test
    fun getDiary() {

      val diary = diaryRepository.createDiary(simpleDiary())
      val result = diaryRepository.getDiary(diary.diaryId)

      assertEquals(diary, result)
    }

    @Test
    fun findDiary() {
      val diary = diaryRepository.createDiary(simpleDiary())
      val result = diaryRepository.findDiary(diary.diaryId)

      assertEquals(diary, result)
    }

    @Test
    fun createDiary() {
      val diary = simpleDiary()
      val result = diaryRepository.createDiary(diary)

      assertEquals(diary, result)
    }
  }

  @Nested
  inner class 실패 {

    @Test
    fun `getDiary - 없는 id를 조회하면 notFoundException이 발생한다`() {

      diaryRepository.createDiary(simpleDiary())
      assertThrows<NotFoundException> {
        diaryRepository.getDiary(absentId)
      }
    }

    @Test
    fun `findDiary - 없는 id를 조회하면 null을 반환한다`() {
      diaryRepository.createDiary(simpleDiary())

      val result = diaryRepository.findDiary(absentId)

      assertNull(result)
    }

    @Test
    fun `createDiary - 이미 존재하는 diaryId로 생성을 시도하면 DuplicateKeyException이 발생한다`() {
      val diary = Diary(title = "simple 제목", content = "간단 content")
      diaryRepository.createDiary(diary)

      assertThrows<DuplicateKeyException> {
        diaryRepository.createDiary(diary)
      }
    }
  }
}
