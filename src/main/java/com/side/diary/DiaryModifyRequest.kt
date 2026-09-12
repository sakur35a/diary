package com.side.diary

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * 클라이언트가 입력할 수 있는 필드만 받는 HTTP 경계다. ID는 서버에서 생성한다. 지금은 매핑이 한 줄이므로 별도 Mapper 클래스 없이 여기서 도메인으로 변환한다.
 */
data class DiaryModifyRequest(
    @field:Size(min = 1, max = 255) val title: String,
    @field:NotBlank val content: String,
) {
    fun toDiary(diaryId: UUID) = Diary(diaryId = diaryId, title = title, content = content)
}
