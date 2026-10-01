package me.aliahad.audioplayer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.forAll

// Feature: theme-support, Property 2: Theme icon reflects current mode
// **Validates: Requirements 2.4**
class ThemeIconPropertyTest : FunSpec({

    test("Theme icon is sun (LightMode) when isNightMode is true, moon (DarkMode) when false") {
        forAll(Arb.boolean()) { isNightMode ->
            val expected = if (isNightMode) Icons.Filled.LightMode else Icons.Filled.DarkMode
            themeToggleIcon(isNightMode) == expected
        }
    }
})
