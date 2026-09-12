package com.side.diary

import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용 사례와 트랜잭션의 경계다. Controller는 HTTP/요청 변환, Repository는 SQL에 집중한다. 지금은 단순 위임이라 서비스 구현을 위한 인터페이스나 별도
 * command 객체는 필요하지 않다.
 *
 * 같은 저장/조회 시나리오를 서비스 DB 테스트로 반복하지 않고 API 통합 테스트로 연결을 확인한다. 서비스 자체에 권한 판단, 분기, 여러 저장소 작업이 생기면 그 규칙에
 * 대한 테스트를 추가한다.
 */
@Service
class DiaryService(private val diaryRepository: DiaryRepository) {

    fun getDiary(diaryId: UUID) = diaryRepository.getDiary(diaryId)

    @Transactional fun createDiary(diary: Diary) = diaryRepository.createDiary(diary)

    @Transactional fun modifyDiary(diary: Diary) = diaryRepository.modifyDiary(diary)
}
