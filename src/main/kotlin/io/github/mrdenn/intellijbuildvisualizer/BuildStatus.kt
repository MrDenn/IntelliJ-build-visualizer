package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Build outcome for a single module after a Gradle build.
 *
 * Ordered by "severity", so that [maxOf] resolves conflicts when multiple compile tasks
 * fire for the same module (e.g. `compileKotlin` and `compileJava`): [FAILED] wins.
 */
enum class BuildStatus(val color: JBColor) {
    COMPILING(JBColor(Color(0xffbf62), Color(0x403523))),
    UP_TO_DATE(JBColor(Color(0x4a7ff6), Color(0x212c44))),
    COMPILED(JBColor(Color(0x4d8b52), Color(0x212f23))),
    FAILED(JBColor(Color(0xd65252), Color(0x3d2120)));
}
