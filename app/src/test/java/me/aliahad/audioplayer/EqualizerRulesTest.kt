package me.aliahad.audioplayer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.forAll

class EqualizerRulesTest : FunSpec({

    val sampleRates = listOf(44_100, 48_000)

    context("presets") {
        test("every preset has one gain per band, inside the band limit") {
            EqPreset.entries.forEach { preset ->
                preset.bandGainsDb shouldHaveSize EQ_BAND_COUNT
                preset.bandGainsDb.forEach { gain -> (gain in -EQ_BAND_LIMIT_DB..EQ_BAND_LIMIT_DB) shouldBe true }
            }
        }

        test("preset ids are unique and resolve back to their preset") {
            EqPreset.entries.map { it.id }.toSet() shouldHaveSize EqPreset.entries.size
            EqPreset.entries.forEach { EqPreset.fromId(it.id) shouldBe it }
            EqPreset.fromId(CUSTOM_PRESET_ID) shouldBe null
        }

        test("the bass presets lift the low end") {
            listOf(EqPreset.BASS_BOOST, EqPreset.SUBWOOFER, EqPreset.DEEP_BASS).forEach { preset ->
                preset.bandGainsDb[1] shouldBeGreaterThan 4.0
            }
        }
    }

    context("settings") {
        test("defaults are neutral, so nothing is processed until the user asks for it") {
            EqualizerSettings().isNeutral shouldBe true
            EqualizerSettings(bassBoostPercent = 40).isNeutral shouldBe false
            EqualizerSettings(enabled = false, bassBoostPercent = 100).isNeutral shouldBe true
        }

        test("choosing a preset keeps bass boost, loudness and the switch") {
            val settings = EqualizerSettings(bassBoostPercent = 70, loudnessPercent = 20)
                .withPreset(EqPreset.ROCK)
            settings.presetId shouldBe EqPreset.ROCK.id
            settings.bandGainsDb shouldBe EqPreset.ROCK.bandGainsDb
            settings.bassBoostPercent shouldBe 70
            settings.loudnessPercent shouldBe 20
        }

        test("moving a band turns the preset into Custom and clamps the gain") {
            val settings = EqualizerSettings().withPreset(EqPreset.POP).withBandGain(band = 0, gainDb = 30.0)
            settings.presetId shouldBe CUSTOM_PRESET_ID
            settings.bandGainsDb[0] shouldBe EQ_BAND_LIMIT_DB
            settings.bandGainsDb.drop(1) shouldBe EqPreset.POP.bandGainsDb.drop(1)
        }

        test("an out-of-range band index is ignored") {
            val settings = EqualizerSettings()
            settings.withBandGain(band = EQ_BAND_COUNT, gainDb = 3.0) shouldBe settings
            settings.withBandGain(band = -1, gainDb = 3.0) shouldBe settings
        }

        test("sanitizing repairs band count, ranges and unknown presets") {
            val repaired = sanitizeEqualizerSettings(
                EqualizerSettings(
                    presetId = "removed_preset",
                    bandGainsDb = listOf(Double.NaN, 99.0, -99.0),
                    bassBoostPercent = 250,
                    loudnessPercent = -5
                )
            )
            repaired.presetId shouldBe CUSTOM_PRESET_ID
            repaired.bandGainsDb shouldHaveSize EQ_BAND_COUNT
            repaired.bandGainsDb.take(3) shouldBe listOf(0.0, EQ_BAND_LIMIT_DB, -EQ_BAND_LIMIT_DB)
            repaired.bassBoostPercent shouldBe 100
            repaired.loudnessPercent shouldBe 0
        }
    }

    context("band gain storage") {
        test("encoding then decoding round-trips valid gains") {
            checkAll(Arb.list(Arb.double(-EQ_BAND_LIMIT_DB, EQ_BAND_LIMIT_DB), EQ_BAND_COUNT..EQ_BAND_COUNT)) { gains ->
                decodeBandGains(encodeBandGains(gains)) shouldBe gains
            }
        }

        test("garbage never throws and always yields ten in-range bands") {
            forAll(Arb.string(0..80)) { garbage ->
                val gains = decodeBandGains(garbage)
                gains.size == EQ_BAND_COUNT && gains.all { it in -EQ_BAND_LIMIT_DB..EQ_BAND_LIMIT_DB }
            }
            decodeBandGains(null) shouldBe List(EQ_BAND_COUNT) { 0.0 }
        }
    }

    context("filters") {
        test("a peaking band has exactly its gain at its centre frequency") {
            checkAll(Arb.element(sampleRates), Arb.element(EQ_BAND_FREQUENCIES_HZ.dropLast(1)), Arb.int(-12..12)) { rate, frequency, gain ->
                val filter = peakingCoefficients(frequency, gain.toDouble(), EQ_BAND_Q, rate)
                filter.magnitudeDb(frequency, rate) shouldBe (gain.toDouble() plusOrMinus 0.01)
            }
        }

        test("a peaking band leaves distant frequencies almost untouched") {
            val filter = peakingCoefficients(62.0, 12.0, EQ_BAND_Q, 44_100)
            filter.magnitudeDb(4_000.0, 44_100) shouldBeLessThan 0.1
        }

        test("the bass shelf lifts the sub-bass by its full gain and leaves the treble alone") {
            sampleRates.forEach { rate ->
                val shelf = lowShelfCoefficients(BASS_BOOST_CORNER_HZ, BASS_BOOST_MAX_DB, rate)
                shelf.magnitudeDb(20.0, rate) shouldBe (BASS_BOOST_MAX_DB plusOrMinus 0.3)
                shelf.magnitudeDb(BASS_BOOST_CORNER_HZ, rate) shouldBe (BASS_BOOST_MAX_DB / 2 plusOrMinus 0.5)
                shelf.magnitudeDb(5_000.0, rate) shouldBe (0.0 plusOrMinus 0.1)
            }
        }

        test("zero gain and unplayable frequencies are identity filters") {
            peakingCoefficients(1_000.0, 0.0, EQ_BAND_Q, 44_100) shouldBe BiquadCoefficients.IDENTITY
            peakingCoefficients(16_000.0, 6.0, EQ_BAND_Q, 22_050) shouldBe BiquadCoefficients.IDENTITY
            lowShelfCoefficients(BASS_BOOST_CORNER_HZ, 0.0, 44_100) shouldBe BiquadCoefficients.IDENTITY
        }

        test("a neutral state builds no filters and no gain change") {
            equalizerFilterChain(EqualizerSettings(), 44_100).shouldBeEmpty()
            equalizerGains(EqualizerSettings(), emptyList(), 44_100) shouldBe EqualizerGains(1.0, 1.0)
        }

        test("the chain holds the shelf plus one filter per non-zero band") {
            val settings = EqualizerSettings(bassBoostPercent = 50).withPreset(EqPreset.DEEP_BASS)
            val nonZeroBands = EqPreset.DEEP_BASS.bandGainsDb.count { it != 0.0 }
            equalizerFilterChain(settings, 48_000) shouldHaveSize nonZeroBands + 1
        }

        test("the auto preamp takes off half of the strongest boost") {
            val settings = EqualizerSettings(bassBoostPercent = 100)
            val chain = equalizerFilterChain(settings, 44_100)
            val maxDb = maxChainGainDb(chain, 44_100)
            maxDb shouldBe (BASS_BOOST_MAX_DB plusOrMinus 0.3)
            val gains = equalizerGains(settings, chain, 44_100)
            gains.preampLinear shouldBe (dbToLinear(-maxDb * AUTO_PREAMP_FRACTION) plusOrMinus 1e-9)
            gains.outputLinear shouldBe 1.0
        }

        test("cuts never raise the preamp above unity") {
            val settings = EqualizerSettings(bandGainsDb = List(EQ_BAND_COUNT) { -6.0 }, presetId = CUSTOM_PRESET_ID)
            val chain = equalizerFilterChain(settings, 44_100)
            equalizerGains(settings, chain, 44_100).preampLinear shouldBe 1.0
        }

        test("full loudness adds its maximum gain") {
            val settings = EqualizerSettings(loudnessPercent = 100)
            equalizerGains(settings, emptyList(), 44_100).outputLinear shouldBe (dbToLinear(LOUDNESS_MAX_DB) plusOrMinus 1e-9)
        }
    }
})
