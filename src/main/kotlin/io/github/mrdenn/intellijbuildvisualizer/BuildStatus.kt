package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Build outcome for a single module after a Gradle build.
 *
 * Each entry carries a theme-aware [color] used as the graph node fill.
 * Entries are ordered by severity so that [maxOf] can be used to resolve
 * conflicts when multiple compile tasks fire for the same module
 * (e.g. `compileKotlin` + `compileJava`): [FAILED] always wins.
 */
enum class BuildStatus(val color: JBColor) {
    /** Module was up-to-date and compilation was skipped. */
//    UP_TO_DATE(JBColor(Color(0xB0, 0xB0, 0xB0), Color(0x6E, 0x6E, 0x6E))),
    UP_TO_DATE(JBColor(Color(0x4a7ff6), Color(0x212c44))),

    /** Module was compiled successfully in this build. */
    COMPILED(JBColor(Color(0x4d8b52), Color(0x212f23))),

    /** Module compilation failed. */
    FAILED(JBColor(Color(0xd65252), Color(0x3d2120)));

    // Phase 3 will add COMPILING(JBColor(...)) between UP_TO_DATE and COMPILED
}
