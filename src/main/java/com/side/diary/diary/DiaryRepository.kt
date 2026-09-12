package com.side.diary.diary

import com.example.jooq.generated.Tables.*
import com.example.jooq.generated.tables.Diaries
import com.side.diary.NotFoundException
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class DiaryRepository(private val dsl: DSLContext) {

    private val logger = LoggerFactory.getLogger(DiaryRepository::class.java)

    fun getDiary(diaryId: UUID): Diary {
        return dsl.selectFrom(DIARIES)
            .where(DIARIES.DIARY_ID.eq(diaryId))
            .and(DIARIES.DELETED.eq(false))
            .fetchOneInto(Diary::class.java)
            .let { it ?: throw NotFoundException("일기를 찾지 못하였습니다.") }
    }

    fun findDiary(diaryId: UUID): Diary? {
        return dsl.selectFrom(DIARIES)
            .where(DIARIES.DIARY_ID.eq(diaryId))
            .fetchOneInto(Diary::class.java)
    }

    fun createDiary(diary: Diary): Diary {

        logger.warn("${diary.diaryId}")

        val rowsAffected =
            dsl.insertInto(Diaries.DIARIES)
                .set(Diaries.DIARIES.DIARY_ID, diary.diaryId)
                .set(Diaries.DIARIES.TITLE, diary.title)
                .set(Diaries.DIARIES.CONTENT, diary.content)
                .set(Diaries.DIARIES.CREATED_AT, Instant.now())
                .set(Diaries.DIARIES.DELETED, false)
                .execute()

        check(rowsAffected == 1) { "Diary insert affected $rowsAffected rows" }

        return diary
    }
}
