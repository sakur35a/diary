package com.side.diary

import jakarta.validation.Valid
import java.net.URI
import java.util.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/diary")
class DiaryController(private val diaryService: DiaryService) {

    @GetMapping("/{diaryId}")
    fun getDiary(@PathVariable diaryId: UUID) = ResponseEntity.ok(diaryService.getDiary(diaryId))

    @PostMapping
    fun createDiary(@RequestBody @Valid request: DiaryCreateRequest): ResponseEntity<Diary> {

        val diary = diaryService.createDiary(request.toDiary())

        return ResponseEntity.created(URI.create("/diary/${diary.diaryId}")).body(diary)
    }
}
