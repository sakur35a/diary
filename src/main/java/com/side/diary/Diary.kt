package com.side.diary

import com.side.UUID_V7_GENERATOR
import java.util.UUID

data class Diary(
    val diaryId: UUID = UUID_V7_GENERATOR.generate(),
    val title: String,
    val content: String,
) {
    init {
        require(diaryId.version() == 7) { "Diary Id는 uuid v7 이어야 합니다. ${diaryId.version()}" }
        require(title.length in 1..255) { "Diary title은 1자 이상 255자 이하여야 합니다." }
        require(content.isNotBlank()) { "Diary content는 빈 문자열일 수 없습니다." }
    }
}
