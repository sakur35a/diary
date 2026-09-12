package com.side.diary

import com.example.jooq.generated.Tables.DIARIES
import com.side.NotFoundException
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class DiaryRepository(private val dsl: DSLContext) {

    /**
     * get/find는 '없을 때 예외인가 null인가'만 다르고, 조회 가능한 행의 범위는 같아야 한다. SQL을 각각 작성하면 삭제 조건 같은 정책이 한쪽에서 빠지기
     * 쉬우므로 findDiary에 위임한다. HTTP 404로의 변환과 번역은 GlobalExceptionHandler가 맡는다.
     */
    fun getDiary(diaryId: UUID): Diary =
        findDiary(diaryId) ?: throw NotFoundException("일기를 찾지 못하였습니다.")

    fun findDiary(diaryId: UUID): Diary? {
        require(diaryId.version() == 7) { "Diary Id는 uuid v7 이어야 합니다. ${diaryId.version()}" }

        // 일반 조회는 항상 미삭제 행만 반환한다. 복구/관리 기능에서 삭제 행이 필요해지면
        // 그 의도가 드러나는 별도 조회를 추가하고, 이 조건을 제거해 일반 조회에 섞지 않는다.
        return dsl.selectFrom(DIARIES)
            .where(DIARIES.DIARY_ID.eq(diaryId))
            .and(DIARIES.DELETED.eq(false))
            .fetchOneInto(Diary::class.java)
    }

    fun createDiary(diary: Diary): Diary {

        // PK 중복은 PostgreSQL 제약에 맡긴다. 사전 SELECT는 동시 INSERT를 막지 못하며
        // 왕복만 늘어난다. 정상 저장을 WARN으로 남기지 않아 실제 경고가 묻히지 않게 한다.
        // newRecord(table, source)는 Record.from(source)로 객체의 값을 레코드에 복사한다.
        // https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/DSLContext.html#newRecord(org.jooq.Table,java.lang.Object)
        // 기본 매핑은 컬럼명과 public 필드/getter의 명명 규칙을 사용하며, 타입도 변환 가능해야 한다.
        // 예: title -> getTitle(), diary_id -> getDiaryId() (Kotlin의 title, diaryId 프로퍼티).
        // https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/Record.html#from(java.lang.Object)
        val rowsAffected =
            dsl.newRecord(DIARIES, diary)
                .apply {
                    createdAt = Instant.now()
                    deleted = false
                }
                // TableRecord.insert()가 INSERT를 실행하고 행 수를 반환하므로 execute()는 필요 없다.
                // https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/TableRecord.html#insert()
                .insert()

        check(rowsAffected == 1) { "Diary insert affected $rowsAffected rows" }

        return diary
    }

    fun modifyDiary(diary: Diary): Diary {
        // TODO(rev): revision 컬럼 도입 후 현재 revision을 WHERE 조건에 포함하고,
        // 갱신 행 수 0을 낙관적 잠금 실패로 구분한다.
        val record =
            dsl.newRecord(DIARIES).apply {
                from(diary, DIARIES.TITLE, DIARIES.CONTENT)
            }

        val rowsAffected =
            dsl.executeUpdate(
                record,
                DIARIES.DIARY_ID.eq(diary.diaryId).and(DIARIES.DELETED.eq(false)),
            )

        if (rowsAffected == 0) {
            throw NotFoundException("일기를 찾지 못하였습니다.")
        }

        return diary
    }
}
