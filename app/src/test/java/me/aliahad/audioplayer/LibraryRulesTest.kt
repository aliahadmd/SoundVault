package me.aliahad.audioplayer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlin.math.sign

class LibraryRulesTest : FunSpec({

    context("isPlayableAudio") {
        test("accepts supported extensions regardless of case or MIME") {
            isPlayableAudio("song.mp3", "audio/mpeg") shouldBe true
            isPlayableAudio("SONG.MP3", null) shouldBe true
            isPlayableAudio("track.flac", "application/octet-stream") shouldBe true
            isPlayableAudio("voice.opus", null) shouldBe true
        }

        test("accepts other audio MIME types without a known extension") {
            isPlayableAudio("recording", "audio/ogg") shouldBe true
        }

        test("rejects playlists even though they report an audio MIME type") {
            isPlayableAudio("playlist.m3u", "audio/x-mpegurl") shouldBe false
            isPlayableAudio("stream.m3u8", null) shouldBe false
            isPlayableAudio("radio.pls", "audio/x-scpls") shouldBe false
            isPlayableAudio("mix", "audio/mpegurl") shouldBe false
            isPlayableAudio("album.cue", null) shouldBe false
        }

        test("rejects hidden and MediaStore-trashed files") {
            isPlayableAudio(".trashed-1790000000-song.mp3", "audio/mpeg") shouldBe false
            isPlayableAudio(".hidden.mp3", "audio/mpeg") shouldBe false
        }

        test("rejects non-audio and nameless documents") {
            isPlayableAudio("notes.txt", "text/plain") shouldBe false
            isPlayableAudio("cover.jpg", "image/jpeg") shouldBe false
            isPlayableAudio(null, "audio/mpeg") shouldBe false
            isPlayableAudio("  ", "audio/mpeg") shouldBe false
        }
    }

    context("NaturalOrderComparator") {
        test("orders numbered files by value, not character by character") {
            listOf("Track 10.mp3", "Track 2.mp3", "Track 1.mp3")
                .sortedWith(NaturalOrderComparator) shouldBe listOf("Track 1.mp3", "Track 2.mp3", "Track 10.mp3")
        }

        test("handles leading zeros and multi-level paths") {
            listOf("Disc 2/Track 1.mp3", "Disc 1/Track 10.mp3", "Disc 1/Track 02.mp3")
                .sortedWith(NaturalOrderComparator) shouldBe
                listOf("Disc 1/Track 02.mp3", "Disc 1/Track 10.mp3", "Disc 2/Track 1.mp3")
        }

        test("is case-insensitive for letters") {
            listOf("banana", "Apple", "cherry").sortedWith(NaturalOrderComparator) shouldBe
                listOf("Apple", "banana", "cherry")
        }

        test("only distinct strings compare unequal, and ordering is antisymmetric") {
            forAll(Arb.string(0..12), Arb.string(0..12)) { a, b ->
                val ab = NaturalOrderComparator.compare(a, b)
                val ba = NaturalOrderComparator.compare(b, a)
                (ab == 0) == (a == b) && ab.sign == -ba.sign
            }
        }
    }

    context("formatPlaybackSpeed") {
        test("shows every preset exactly") {
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).map(::formatPlaybackSpeed) shouldBe
                listOf("0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        }
    }
})
