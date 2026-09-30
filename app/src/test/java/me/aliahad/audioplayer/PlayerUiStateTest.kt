package me.aliahad.audioplayer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// Replaces the JUnit 4 template test, which never ran: unit tests use the JUnit Platform (kotest)
// and the project has no JUnit Vintage engine.
class PlayerUiStateTest : FunSpec({

    test("bookmark dialog position comes from the draft captured at tap time") {
        val draft = BookmarkDraft(
            positionMs = 42_000L,
            audioFileUri = "content://tree/doc/long.mp3",
            folderUri = "content://tree",
            trackTitle = "Long Lecture"
        )
        PlayerUiState().bookmarkDialogPositionMs shouldBe null
        PlayerUiState(bookmarkDraft = draft).bookmarkDialogPositionMs shouldBe 42_000L
    }

    test("formatTimestamp never renders negative positions") {
        formatTimestamp(-5_000L) shouldBe "0:00"
    }
})
