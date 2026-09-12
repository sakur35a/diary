package com.side.docs

import java.util.regex.Pattern
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern

/** REST Docs 요청 본문의 실제 값과 문서용 예시 값을 치환하는 전처리기다. */
fun requestExamples(vararg replacements: Pair<String, String>): OperationRequestPreprocessor =
    preprocessRequest(
        *replacements
            .map { (original, example) ->
                replacePattern(Pattern.compile(Pattern.quote(original)), example)
            }
            .toTypedArray()
    )
