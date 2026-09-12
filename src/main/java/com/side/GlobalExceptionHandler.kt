package com.side

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
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
    ): ProblemDetail =
        badRequest(
            code = "METHOD_ARGUMENT_TYPE_MISMATCH",
            typePath = "method-argument-type-mismatch",
            detail =
                message(
                    "error.invalid-parameter",
                    arrayOf(ex.name, ex.requiredType?.simpleName ?: "valid type"),
                ),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ProblemDetail {
        val locale = LocaleContextHolder.getLocale()
        val detail =
            ex.bindingResult.allErrors.joinToString("; ") { error ->
                val description = messageSource.getMessage(error, locale)
                if (error is FieldError) "${error.field}: $description" else description
            }

        return badRequest(
            code = "METHOD_ARGUMENT_NOT_VALID",
            typePath = "method-argument-not-valid",
            detail = detail,
        )
    }

    private fun badRequest(code: String, typePath: String, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).apply {
            log.warn("Bad request: code={}, detail={}", code, detail)
            setProperty("code", code)
            type = URI.create("${domain.trimEnd('/')}/problems/$typePath")
        }

    private fun message(code: String, args: Array<Any> = emptyArray()): String =
        messageSource.getMessage(code, args, LocaleContextHolder.getLocale())
}
