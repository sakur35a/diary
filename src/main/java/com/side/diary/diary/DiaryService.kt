package com.side.diary.diary

import java.util.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiaryService(private val diaryRepository: DiaryRepository) {

    fun getDiary(diaryId: UUID) = diaryRepository.getDiary(diaryId)

    @Transactional fun createDiary(diary: Diary) = diaryRepository.createDiary(diary)
}
