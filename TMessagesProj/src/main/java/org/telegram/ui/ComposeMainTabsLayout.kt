package org.telegram.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.glass.GlassTabView

/**
 * Compose-owned layout for the main navigation bar. The existing tab content is hosted as AndroidViews
 * during this first migration slice so Lottie, avatars, badges, accessibility and click behavior stay intact.
 * New navigation layout and visibility behavior live in Compose; the old custom AnimatedLinearLayout is
 * retained for other, not-yet-migrated tab strips.
 */
class ComposeMainTabsLayout(context: Context, @Suppress("UNUSED_PARAMETER") resourcesProvider: Theme.ResourcesProvider?) : AbstractComposeView(context) {
    private class Entry(val view: View, initiallyVisible: Boolean) {
        var visible by mutableStateOf(initiallyVisible)
        var animateVisibility by mutableStateOf(false)
    }

    private val entries = mutableStateListOf<Entry>()
    private var maxWidthPx by mutableStateOf(0)

    init {
        clipChildren = false
        clipToPadding = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    @Composable
    @UiComposable
    override fun Content() {
        val density = LocalDensity.current
        val maxOuterWidth = with(density) { maxWidthPx.toDp() }
        val rowMaxWidth = (maxOuterWidth - 24.dp).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = rowMaxWidth).height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                entries.forEach { entry ->
                    val visibility by animateFloatAsState(
                        targetValue = if (entry.visible) 1f else 0f,
                        animationSpec = if (entry.animateVisibility) tween(durationMillis = 180) else snap(),
                        label = "main-tab-visibility"
                    )
                    if (entry.visible || visibility > 0.001f) {
                        val labelWidthDp = (entry.view as? GlassTabView)?.measureTextWidth()?.div(density.density) ?: 48f
                        val adaptiveTabWeight = labelWidthDp + 16f
                        AndroidView(
                            factory = { entry.view },
                            modifier = Modifier
                                .weight((adaptiveTabWeight * visibility).coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .graphicsLayer { alpha = visibility },
                            update = { it.isEnabled = this@ComposeMainTabsLayout.isEnabled }
                        )
                    }
                }
            }
        }
    }

    fun setMaxWidth(widthPx: Int) {
        if (maxWidthPx != widthPx) {
            maxWidthPx = widthPx
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        if (maxWidthPx > 0 && requestedWidth > maxWidthPx) {
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY), heightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun addTab(view: View, visible: Boolean) {
        if (entries.none { it.view === view }) entries.add(Entry(view, visible))
    }

    fun setViewVisible(view: View, visible: Boolean, animated: Boolean) {
        val entry = entries.firstOrNull { it.view === view } ?: return
        if (entry.visible != visible) {
            entry.animateVisibility = animated
            entry.visible = visible
        }
    }

    /** The child Views are hosted by AndroidView, so expose their Compose row centers to legacy swipe code. */
    fun getTabCenterX(view: View): Float {
        val visibleEntries = entries.filter { it.visible }
        val index = visibleEntries.indexOfFirst { it.view === view }
        if (index < 0 || visibleEntries.isEmpty()) return width / 2f

        val horizontalInset = AndroidUtilities.dp(12f)
        val availableWidth = (width - horizontalInset * 2).coerceAtLeast(0)
        val maxRowWidth = if (maxWidthPx > 0) (maxWidthPx - horizontalInset * 2).coerceAtLeast(0) else availableWidth
        val rowWidth = minOf(availableWidth, maxRowWidth)
        val rowLeft = (width - rowWidth) / 2f
        return rowLeft + rowWidth * (index + 0.5f) / visibleEntries.size
    }
}
