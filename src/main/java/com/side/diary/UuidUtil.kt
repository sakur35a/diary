package com.side.diary

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator

// Kotlin의 최상위 선언으로 충분하다. 생성기를 공유하기 위한 빈 Util 클래스는 필요하지 않다.
val UUID_V7_GENERATOR: TimeBasedEpochGenerator = Generators.timeBasedEpochGenerator()
