package me.aliahad.audioplayer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll

class SkipIntervalsTest : FunSpec({

    context("sanitizeSkipInterval") {
        test("keeps every offered option") {
            SKIP_INTERVAL_OPTIONS_MS.forEach { sanitizeSkipInterval(it) shouldBe it }
        }

        test("falls back to the default for missing or unknown values") {
            sanitizeSkipInterval(null) shouldBe DEFAULT_SKIP_INTERVAL_MS
            sanitizeSkipInterval(0L) shouldBe DEFAULT_SKIP_INTERVAL_MS
            sanitizeSkipInterval(-10_000L) shouldBe DEFAULT_SKIP_INTERVAL_MS
            sanitizeSkipInterval(15_000L) shouldBe DEFAULT_SKIP_INTERVAL_MS
        }

        test("the default is one of the options") {
            (DEFAULT_SKIP_INTERVAL_MS in SKIP_INTERVAL_OPTIONS_MS) shouldBe true
        }
    }

    context("skipTargetPosition") {
        test("moves by the offset inside the track") {
            skipTargetPosition(positionMs = 30_000L, durationMs = 180_000L, offsetMs = -10_000L) shouldBe 20_000L
            skipTargetPosition(positionMs = 30_000L, durationMs = 180_000L, offsetMs = 60_000L) shouldBe 90_000L
        }

        test("clamps a skip back to the start of the track") {
            skipTargetPosition(positionMs = 3_000L, durationMs = 180_000L, offsetMs = -10_000L) shouldBe 0L
        }

        test("clamps a skip forward to the end of the track") {
            skipTargetPosition(positionMs = 175_000L, durationMs = 180_000L, offsetMs = 20_000L) shouldBe 180_000L
        }

        test("does not clamp forward when the duration is unknown") {
            skipTargetPosition(positionMs = 175_000L, durationMs = null, offsetMs = 20_000L) shouldBe 195_000L
            skipTargetPosition(positionMs = 175_000L, durationMs = -1L, offsetMs = 20_000L) shouldBe 195_000L
        }

        test("treats a negative current position as the start") {
            skipTargetPosition(positionMs = -500L, durationMs = 180_000L, offsetMs = 5_000L) shouldBe 5_000L
        }

        test("always lands inside [0, duration]") {
            forAll(
                Arb.long(0L..10_000_000L),
                Arb.long(0L..10_000_000L),
                Arb.element(SKIP_INTERVAL_OPTIONS_MS),
                Arb.element(listOf(-1L, 1L))
            ) { position, duration, interval, sign ->
                skipTargetPosition(position, duration, sign * interval) in 0L..duration
            }
        }
    }

    context("skipIntervalLabel") {
        test("labels the offered options") {
            skipIntervalLabel(5_000L) shouldBe SkipIntervalLabel(5, inMinutes = false)
            skipIntervalLabel(10_000L) shouldBe SkipIntervalLabel(10, inMinutes = false)
            skipIntervalLabel(20_000L) shouldBe SkipIntervalLabel(20, inMinutes = false)
            skipIntervalLabel(60_000L) shouldBe SkipIntervalLabel(1, inMinutes = true)
        }

        test("keeps seconds for steps that are not whole minutes") {
            skipIntervalLabel(90_000L) shouldBe SkipIntervalLabel(90, inMinutes = false)
            skipIntervalLabel(120_000L) shouldBe SkipIntervalLabel(2, inMinutes = true)
        }
    }
})
