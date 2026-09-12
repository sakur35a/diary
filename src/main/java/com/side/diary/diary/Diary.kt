package com.side.diary.diary

import com.side.diary.UUID_V7_GENERATOR
import java.util.UUID

data class Diary(
    val diaryId: UUID = UUID_V7_GENERATOR.generate(),
    val title: String,
    val content: String,
) {
    init {
        require(diaryId.version() == 7) { "Diary Id는 uuid v7 이어야 합니다. ${diaryId.version()}" }
    }
}
