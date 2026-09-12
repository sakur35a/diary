package com.side.diary

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler(
    @Value("\${app.domain}") private val domain: String,
    private val messageSource: MessageSource,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            message("error.diary.not-found"),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException
    ): ProblemDetail {

        log.error(ex.message)
        val expectedType = ex.requiredType?.simpleName ?: "valid type"

        val pb =
            ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    message(
                        "error.invalid-parameter",
                        arrayOf(ex.name, expectedType),
                    ),
                )
                .also { it.setProperty("code", "METHOD_ARGUMENT_TYPE_MISMATCH") }
                .also { it.type = URI.create("${domain}/problems/method-argument-type-mismatch") }

        return pb
    }

    private fun message(code: String, args: Array<Any> = emptyArray()): String =
        messageSource.getMessage(code, args, LocaleContextHolder.getLocale())
}
