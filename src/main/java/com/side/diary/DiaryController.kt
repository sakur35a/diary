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
    fun createDiary(@RequestBody @Valid request: DiaryCreateRequest): ResponseEntity<Void> {

        val diary = diaryService.createDiary(request.toDiary())

        return ResponseEntity.created(URI.create("/diary/${diary.diaryId}")).build()
    }

    @PutMapping("/{diaryId}")
    fun modifyDiary(
        @PathVariable diaryId: UUID,
        @RequestBody @Valid request: DiaryModifyRequest,
    ): ResponseEntity<Void> {

        diaryService.modifyDiary(request.toDiary(diaryId))

        return ResponseEntity.noContent().build()
    }
}
