/*
 * SaltUI
 * Copyright (C) 2026 Moriafly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from SaltUI feature/haze-glass, commit da471d23d35b39a71d64006caa47a4c1b615834d.
// Keep the upstream implementation local until these APIs are released in SaltUI.
package com.lonx.lyrico.ui.components.effect.glass

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue

internal object GlassStyles {
    fun regular(isDarkTheme: Boolean): GlassStyle = GlassStyle.regular.then {
        optics {
            copy(
                depth = OpticalSizeValue.Fixed(1f),
                blurRadius = OpticalSizeValue.Fixed(2.5.dp)
            )
        }
        specularIntensity(0f)
        // Follow SaltTheme for the Regular preset's light or dark appearance.
        if (isDarkTheme) {
            edgeShadow(Color(0x33000000))
            ambientResponse(0.08f)
            contrast(0.08f)
            whitePoint(-0.22f)
            chromaMultiplier(1.1f)
        } else {
            edgeShadow(GlassDefaults.edgeShadow)
            ambientResponse(GlassDefaults.ambientResponse)
            contrast(GlassDefaults.contrast)
            whitePoint(GlassDefaults.whitePoint)
            chromaMultiplier(GlassDefaults.chromaMultiplier)
        }
    }

    fun transparent(isDarkTheme: Boolean): GlassStyle = regular(isDarkTheme).then {
        tint(Color.Transparent)
        contrast(0f)
        whitePoint(0f)
        chromaMultiplier(1f)
    }

    fun blur(isDarkTheme: Boolean): HazeBlurStyle = HazeBlurStyle {
        backgroundColor(Color.Transparent)
        colorEffects(
            if (isDarkTheme) {
                listOf(
                    HazeColorEffect.tint(color = Color(0x35666666)),
                    HazeColorEffect.tint(
                        color = Color(0x15333333),
                        blendMode = BlendMode.Softlight
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x99000000),
                        blendMode = BlendMode.Overlay
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x18000000),
                        blendMode = BlendMode.Luminosity
                    )
                )
            } else {
                listOf(
                    HazeColorEffect.tint(
                        color = Color(0x65DBDBDB),
                        blendMode = BlendMode.Softlight
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x38EFEFEF),
                        blendMode = BlendMode.Plus
                    )
                )
            }
        )
        blurRadius(16.dp)
        noiseFactor(0.01f)
    }
}
