package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Build outcome for a single module after a Gradle build.
 *
 * Ordered by "severity", so that [maxOf] resolves conflicts when multiple compile tasks
 * fire for the same module (e.g. `compileKotlin` and `compileJava`): [FAILED] wins.
 */
enum class BuildStatus(val fillColor: JBColor, val strokeColor: JBColor) {
    COMPILING(
        fillColor = JBColor(Color(0xfff4e0), Color(0x403523)),
        strokeColor = JBColor(Color(0xffc219), Color(0xffbf62))
    ),
    UP_TO_DATE(
        fillColor = JBColor(Color(0xe3ecfd), Color(0x212c44)),
        strokeColor = JBColor(Color(0x2e69ee), Color(0x4a7ff6))
    ),
    COMPILED(
        fillColor = JBColor(Color(0xf0fcf1), Color(0x212f23)),
        strokeColor = JBColor(Color(0x1e7f35), Color(0x4d8b52))
    ),
    FAILED(
        fillColor = JBColor(Color(0xfce6e7), Color(0x3d2120)),
        strokeColor = JBColor(Color(0xf30001), Color(0xd65252))
    )
}
