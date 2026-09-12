package com.side.diary.diary

import java.net.URI
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/diary")
class DiaryController(private val diaryService: DiaryService) {

    @GetMapping("/{diaryId}")
    fun getDiary(@PathVariable diaryId: UUID) = ResponseEntity.ok(diaryService.getDiary(diaryId))

    @PostMapping
    fun createDiary(@RequestBody request: DiaryCreateRequest): ResponseEntity<Diary> {

        val diary = diaryService.createDiary(request.toDiary())

        return ResponseEntity.created(URI.create("/diary/${diary.diaryId}")).body(diary)
    }
}
