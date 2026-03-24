package io.github.mrdenn.intellijbuildvisualizer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.util.mxConstants
import com.mxgraph.util.mxStyleUtils
import com.mxgraph.view.mxGraph
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.pow

/**
 * Renders a [ModuleNode] dependency graph using JGraphX with Light and Dark theme integration.
 * Uses a top-to-bottom hierarchical layout appropriate for dependency DAGs.
 */
class DependencyGraphPanel(
    jGraphTGraph: Graph<ModuleNode, DefaultEdge>
) : JPanel(BorderLayout()) {

    private val graph = mxGraph()
    private val graphComponent: mxGraphComponent
    private var initialCenterDone = false
    private var cellMap = mapOf<ModuleNode, Any>()

    init {
        configureStyles()
        graph.isAutoSizeCells = true
        populateGraph(jGraphTGraph)
        applyLayout()
        applyMargins()

        graphComponent = mxGraphComponent(graph).apply {
            isConnectable = false
            isPanning = false
            setDragEnabled(false)
            setToolTips(true)

            viewport.isOpaque = true
            viewport.background = UIUtil.getPanelBackground()
            background = UIUtil.getPanelBackground()

            // Used for GitHub theme screenshots
//            val screenshotBg = Color(0x0D, 0x11, 0x17)
//            viewport.background = screenshotBg
//            background = screenshotBg
        }

        // Default graph interactions are turned off to accommodate custom implementations
        graph.isCellsEditable = false
        graph.isCellsResizable = false
        graph.isCellsDeletable = false
        graph.isCellsMovable = false
        graph.isCellsDisconnectable = false

        val interactionHandler = GraphInteractionHandler()
        graphComponent.graphControl.apply {
            addMouseWheelListener(interactionHandler)
            addMouseListener(interactionHandler)
            addMouseMotionListener(interactionHandler)
        }

        add(buildToolbar(), BorderLayout.EAST)
        add(graphComponent, BorderLayout.CENTER)
    }

    /**
     * Builds a slim vertical toolbar with zoom controls, styled according to default IntelliJ UI.
     */
    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Zoom In", "Zoom in", AllIcons.Graph.ZoomIn) {
                override fun actionPerformed(e: AnActionEvent) = graphComponent.zoomIn()
            })
            add(object : AnAction("Zoom Out", "Zoom out", AllIcons.Graph.ZoomOut) {
                override fun actionPerformed(e: AnActionEvent) = graphComponent.zoomOut()
            })
            add(object : AnAction("Reset Zoom", "Reset to 100%", AllIcons.Graph.ActualZoom) {
                override fun actionPerformed(e: AnActionEvent) = graphComponent.zoomActual()
            })
        }

        return ActionManager.getInstance()
            .createActionToolbar("DependencyGraphToolbar", group, false)
            .apply { targetComponent = graphComponent }
            .component
    }

    /**
     * Handles Ctrl+scroll zoom (centered on cursor) and middle-mouse-drag pan.
     * Non-Ctrl scroll is left unconsumed so touchpad two-finger swipe still works.
     */
    private inner class GraphInteractionHandler : MouseAdapter(), MouseWheelListener {
        private var isPanning = false
        private var panStart = Point(0, 0)
        private var panViewStart = Point(0, 0)

        override fun mouseWheelMoved(e: MouseWheelEvent) {
            if (e.isControlDown) {
                zoomAtPoint(e.point, 1.1.pow(-e.preciseWheelRotation))
                e.consume()
            }
            // Non-Ctrl scroll is not consumed
        }

        override fun mousePressed(e: MouseEvent) {
            if (SwingUtilities.isMiddleMouseButton(e)) {
                isPanning = true
                panStart = e.locationOnScreen
                panViewStart = graphComponent.viewport.viewPosition
                graphComponent.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }
        }

        /**
         * Zooms while keeping the graph point under the cursor fixed.
         * [mxGraphComponent.zoom] re-centers the viewport internally,
         * so the view position is overridden immediately after.
         */
        private fun zoomAtPoint(mousePoint: Point, factor: Double) {
            val view = graph.view
            val oldScale = view.scale
            val viewport = graphComponent.viewport
            val vp = viewport.viewPosition
            val graphX = (vp.x + mousePoint.x) / oldScale
            val graphY = (vp.y + mousePoint.y) / oldScale
            graphComponent.zoom(factor)
            val newScale = view.scale
            viewport.viewPosition = Point(
                (graphX * newScale - mousePoint.x).toInt().coerceAtLeast(0),
                (graphY * newScale - mousePoint.y).toInt().coerceAtLeast(0)
            )
        }

        override fun mouseReleased(e: MouseEvent) {
            if (isPanning && SwingUtilities.isMiddleMouseButton(e)) {
                isPanning = false
                graphComponent.cursor = Cursor.getDefaultCursor()
            }
        }

        override fun mouseDragged(e: MouseEvent) {
            if (isPanning) {
                val screen = e.locationOnScreen
                val dx = panStart.x - screen.x
                val dy = panStart.y - screen.y
                graphComponent.viewport.viewPosition = Point(
                    (panViewStart.x + dx).coerceAtLeast(0),
                    (panViewStart.y + dy).coerceAtLeast(0)
                )
            }
        }
    }

    /**
     * Replaces the graph content, re-applies layout and margins, and re-centers.
     */
    fun rebuild(newGraph: Graph<ModuleNode, DefaultEdge>) {
        graph.model.beginUpdate()
        try {
            // removeCells() silently fails here, so children are iterated manually
            val parent = graph.defaultParent
            while (graph.model.getChildCount(parent) > 0) {
                graph.model.remove(graph.model.getChildAt(parent, 0))
            }
        } finally {
            graph.model.endUpdate()
        }
        populateGraph(newGraph)
        applyLayout()
        applyMargins()
        graphComponent.refresh()
        SwingUtilities.invokeLater(::centerGraph)
    }

    /**
     * Overrides only [mxConstants.STYLE_FILLCOLOR], preserving all other vertex styles.
     */
    fun applyBuildStatuses(statuses: Map<ModuleNode, BuildStatus>) {
        if (statuses.isEmpty()) return
        graph.model.beginUpdate()
        try {
            for ((node, status) in statuses) {
                val cell = cellMap[node] ?: continue
                val currentStyle = graph.model.getStyle(cell) ?: ""
                val newStyle = mxStyleUtils.setStyle(
                    currentStyle, mxConstants.STYLE_FILLCOLOR, colorToHex(status.color)
                )
                graph.model.setStyle(cell, newStyle)
            }
        } finally {
            graph.model.endUpdate()
        }
        graphComponent.refresh()
    }

    /**
     * Uses [mxGraph.moveCells] rather than `graph.view.translate` because the
     * latter is silently reset to (0,0) by [mxGraphComponent.zoom].
     */
    private fun applyMargins() {
        val margin = JBUI.scale(100)
        graph.moveCells(
            graph.getChildCells(graph.defaultParent, true, true),
            margin.toDouble(), margin.toDouble()
        )
        graph.border = margin
    }

    /**
     * Resolves [JBColor] at init time; not dynamically adjusted on theme change.
     */
    private fun configureStyles() {
        val vertexStyle = graph.stylesheet.defaultVertexStyle
        val edgeStyle = graph.stylesheet.defaultEdgeStyle

        val fillColor = JBColor(Color(0xE8, 0xF0, 0xFE), Color(0x3C, 0x3F, 0x41))
        val borderColor = JBColor(Color(0x6B, 0x9B, 0xD2), Color(0x5E, 0x6A, 0x75))
        val fontColor = JBColor(Gray._26, Gray._187)
        vertexStyle[mxConstants.STYLE_FILLCOLOR] = colorToHex(fillColor)
        vertexStyle[mxConstants.STYLE_STROKECOLOR] = colorToHex(borderColor)
        vertexStyle[mxConstants.STYLE_FONTCOLOR] = colorToHex(fontColor)

        vertexStyle[mxConstants.STYLE_ROUNDED] = true
        vertexStyle[mxConstants.STYLE_ARCSIZE] = 50
        vertexStyle[mxConstants.STYLE_SHADOW] = false

        vertexStyle[mxConstants.STYLE_FONTSIZE] = JBUI.scaleFontSize(12f)
        vertexStyle[mxConstants.STYLE_FONTSTYLE] = 0

        vertexStyle[mxConstants.STYLE_ALIGN] = mxConstants.ALIGN_CENTER
        vertexStyle[mxConstants.STYLE_VERTICAL_ALIGN] = mxConstants.ALIGN_MIDDLE
        vertexStyle[mxConstants.STYLE_LABEL_POSITION] = mxConstants.ALIGN_CENTER
        vertexStyle[mxConstants.STYLE_VERTICAL_LABEL_POSITION] = mxConstants.ALIGN_MIDDLE

        // Asymmetric padding to adjust for badly centered mxGraph text rendering
        val padding = JBUI.scale(8)
        vertexStyle[mxConstants.STYLE_SPACING_TOP] = padding
        vertexStyle[mxConstants.STYLE_SPACING_BOTTOM] = padding * 6 / 10
        vertexStyle[mxConstants.STYLE_SPACING_LEFT] = padding * 10 / 9
        vertexStyle[mxConstants.STYLE_SPACING_RIGHT] = padding

        val edgeColor = JBColor(Gray._136, Gray._119)
        edgeStyle[mxConstants.STYLE_STROKECOLOR] = colorToHex(edgeColor)
        edgeStyle[mxConstants.STYLE_STROKEWIDTH] = 1.5
        edgeStyle[mxConstants.STYLE_ENDARROW] = mxConstants.ARROW_CLASSIC
        edgeStyle[mxConstants.STYLE_EDGE] = mxConstants.EDGESTYLE_ORTHOGONAL
        edgeStyle[mxConstants.STYLE_ROUNDED] = true
        edgeStyle[mxConstants.STYLE_ENDSIZE] = 6
    }

    /**
     * Converts the JGraphT graph to mxGraph cells, building [cellMap] for status coloring.
     */
    private fun populateGraph(jGraphTGraph: Graph<ModuleNode, DefaultEdge>) {
        val parent = graph.defaultParent
        graph.model.beginUpdate()
        try {
            val cells = mutableMapOf<ModuleNode, Any>()

            for (vertex in jGraphTGraph.vertexSet()) {
                val label = vertex.displayName
                val cell = graph.insertVertex(parent, null, label, 0.0, 0.0, 0.0, 0.0)
                graph.updateCellSize(cell)
                cells[vertex] = cell
            }

            for (edge in jGraphTGraph.edgeSet()) {
                val sourceCell = cells[jGraphTGraph.getEdgeSource(edge)]
                val targetCell = cells[jGraphTGraph.getEdgeTarget(edge)]
                if (sourceCell != null && targetCell != null) {
                    graph.insertEdge(parent, null, "", sourceCell, targetCell)
                }
            }

            cellMap = cells
        } finally {
            graph.model.endUpdate()
        }
    }

    private fun applyLayout() {
        val layout = mxHierarchicalLayout(graph, SwingConstants.NORTH)
        layout.interRankCellSpacing = JBUI.scale(60).toDouble()
        layout.intraCellSpacing = JBUI.scale(20).toDouble()
        layout.execute(graph.defaultParent)
    }

    /**
     * Centers the graph on first display; deferred to after the first Swing layout pass.
     */
    override fun addNotify() {
        super.addNotify()
        if (!initialCenterDone) {
            initialCenterDone = true
            SwingUtilities.invokeLater(::centerGraph)
        }
    }

    private fun centerGraph() {
        val viewport = graphComponent.viewport
        val contentSize = graphComponent.graphControl.preferredSize
        val viewSize = viewport.extentSize
        viewport.viewPosition = Point(
            ((contentSize.width - viewSize.width) / 2).coerceAtLeast(0),
            ((contentSize.height - viewSize.height) / 2).coerceAtLeast(0)
        )
    }

    companion object {
        private fun colorToHex(color: Color): String =
            "#%02X%02X%02X".format(color.red, color.green, color.blue)
    }
}
