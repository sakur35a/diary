package com.side.diary.diary

data class DiaryCreateRequest(
    val title: String,
    val content: String,
) {
    fun toDiary() = Diary(title = title, content = content)
}
