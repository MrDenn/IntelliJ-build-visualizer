package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.BuildVisualizerMessageBundle"

internal object BuildVisualizerMessageBundle {
    private val instance = DynamicBundle(BuildVisualizerMessageBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): String {
        return instance.getMessage(key, *params)
    }

    @JvmStatic
    fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<String> {
        return instance.getLazyMessage(key, *params)
    }
}