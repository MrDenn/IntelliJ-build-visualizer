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
 * Swing panel that renders a JGraphT directed graph as a visual dependency graph
 * using JGraphX.
 *
 * Applies IntelliJ theme colors via [JBColor] so that the graph integrates
 * visually with both light and dark IDE themes. Uses a hierarchical layout
 * (top-to-bottom) appropriate for dependency DAGs.
 *
 * @param jGraphTGraph the directed graph to visualize, where vertices are module
 *   names and edges represent "depends on" relationships
 */
class DependencyGraphPanel(
    jGraphTGraph: Graph<String, DefaultEdge>
) : JPanel(BorderLayout()) {

    private val graph = mxGraph()
    private val graphComponent: mxGraphComponent
    private var initialCenterDone = false

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
        }

        // Turn most default user interactions with the graph off
        graph.isCellsEditable = false
        graph.isCellsResizable = false
        graph.isCellsDeletable = false
        graph.isCellsMovable = false
        graph.isCellsDisconnectable = false

        // Register a handler for graph interactions
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
     *
     * The panel includes a 'Zoom In', 'Zoom Out' and 'Reset Zoom' buttons
     * that use built-in sleek AllIcons.Graph icons.
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
     * Handles zoom (Ctrl+scroll, centered on cursor) and pan (middle-mouse drag).
     *
     * Plain scroll events are ignored, otherwise touchpad two-finger swipe wouldn't work.
     * Touchpad pinch-to-zoom should send Ctrl+scroll on most platforms and
     * therefore trigger zoom here correctly.
     */
    private inner class GraphInteractionHandler : MouseAdapter(), MouseWheelListener {
        private var isPanning = false
        private var panStart = Point(0, 0)
        private var panViewStart = Point(0, 0)

        // Overridden for the zoom to work properly
        override fun mouseWheelMoved(e: MouseWheelEvent) {
            if (e.isControlDown) {
                // Formula for the zoom is 1.1^(-rotation)
                // Should be around 10% per scroll notch and smooth for touchpads
                zoomAtPoint(e.point, 1.1.pow(-e.preciseWheelRotation))
                e.consume()
            }
            // Non-Ctrl scroll is not consumed
        }

        // Overridden for the zoom to work properly
        override fun mousePressed(e: MouseEvent) {
            if (SwingUtilities.isMiddleMouseButton(e)) {
                isPanning = true
                panStart = e.locationOnScreen
                panViewStart = graphComponent.viewport.viewPosition
                graphComponent.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }
        }

        // Manual implementation of zooming
        private fun zoomAtPoint(mousePoint: Point, factor: Double) {
            val view = graph.view
            val oldScale = view.scale
            val viewport = graphComponent.viewport
            val vp = viewport.viewPosition
            // Graph-space point under cursor - must stay fixed after zoom
            val graphX = (vp.x + mousePoint.x) / oldScale
            val graphY = (vp.y + mousePoint.y) / oldScale
            // zoom() applies scale constraints, fires events, and adjusts scroll to
            // viewport center; we override that scroll position immediately after
            graphComponent.zoom(factor)
            val newScale = view.scale
            viewport.viewPosition = Point(
                (graphX * newScale - mousePoint.x).toInt().coerceAtLeast(0),
                (graphY * newScale - mousePoint.y).toInt().coerceAtLeast(0)
            )
        }

        // Overridden for panning to work properly
        override fun mouseReleased(e: MouseEvent) {
            if (isPanning && SwingUtilities.isMiddleMouseButton(e)) {
                isPanning = false
                graphComponent.cursor = Cursor.getDefaultCursor()
            }
        }

        // Overridden for panning to work properly
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
     * Replaces the current graph content with a new JGraphT graph.
     *
     * Clears all existing cells, repopulates from [newGraph], re-applies the
     * hierarchical layout and margins, then re-centers the viewport.
     * The [mxGraph] instance, styles, toolbar, and interaction handlers are preserved.
     */
    fun rebuild(newGraph: Graph<String, DefaultEdge>) {
        graph.model.beginUpdate()
        try {
            // Manual, child-by-child deletion is used, because removeCells did not work
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
     * Adds uniform margin around the graph content by shifting all cells after layout.
     *
     * Uses [mxGraph.moveCells] rather than [mxGraph.getView] translate to avoid
     * the translation being silently reset to (0,0) by [mxGraphComponent.zoom].
     */
    private fun applyMargins() {
        // Add uniform margin around the graph content by shifting all cells after layout

        // Using moveCells rather than graph.view.translate avoids the translation being silently reset to (0,0)
        // by internal logic of graphComponent.zoom(). Otherwise, zooming resets the margins.
        val margin = JBUI.scale(100)
        graph.moveCells(
            graph.getChildCells(graph.defaultParent, true, true),
            margin.toDouble(), margin.toDouble()
        )
        // Reserves space equal to margin on the right and bottom
        graph.border = margin
    }

    /**
     * Configures mxGraph vertex and edge styles to match the current IntelliJ theme.
     *
     * Uses [JBColor] which resolves to the appropriate color for the active theme
     * at the time of graph initialization.
     * (The colors are not dynamically adjusted when theme is changed on-the-fly)
     */
    private fun configureStyles() {

        // Retrieve default style maps for vertices and edges to modify and apply to graph
        val vertexStyle = graph.stylesheet.defaultVertexStyle
        val edgeStyle = graph.stylesheet.defaultEdgeStyle


        // Set colors for all vertices:
        // - Fill color    (light mode: muted blue, Dark mode: dark gray)
        // - Border color  (Light mode: sky blue,   Dark mode: dark gray)
        // - Text color    (Light mode: dark gray,  Dark mode: light gray)

        val fillColor = JBColor(Color(0xE8, 0xF0, 0xFE), Color(0x3C, 0x3F, 0x41))
        val borderColor = JBColor(Color(0x6B, 0x9B, 0xD2), Color(0x5E, 0x6A, 0x75))
        val fontColor = JBColor(Gray._26, Gray._187)
        vertexStyle[mxConstants.STYLE_FILLCOLOR] = colorToHex(fillColor)
        vertexStyle[mxConstants.STYLE_STROKECOLOR] = colorToHex(borderColor)
        vertexStyle[mxConstants.STYLE_FONTCOLOR] = colorToHex(fontColor)

        // Set vertex box style parameters:
        // Boxes with moderately rounder corners, without drop shadows
        vertexStyle[mxConstants.STYLE_ROUNDED] = true
        vertexStyle[mxConstants.STYLE_ARCSIZE] = 50
        vertexStyle[mxConstants.STYLE_SHADOW] = false

        // Set font style parameters:
        // Regular text using IntelliJ's DPI scaling factor for high-DPI displays
        vertexStyle[mxConstants.STYLE_FONTSIZE] = JBUI.scaleFontSize(12f)
        vertexStyle[mxConstants.STYLE_FONTSTYLE] = 0

        vertexStyle[mxConstants.STYLE_ALIGN] = mxConstants.ALIGN_CENTER
        vertexStyle[mxConstants.STYLE_VERTICAL_ALIGN] = mxConstants.ALIGN_MIDDLE
        vertexStyle[mxConstants.STYLE_LABEL_POSITION] = mxConstants.ALIGN_CENTER
        vertexStyle[mxConstants.STYLE_VERTICAL_LABEL_POSITION] = mxConstants.ALIGN_MIDDLE

        // Manually set padding to adjust for terrible text centering done by Swing
        val padding = JBUI.scale(8)
        vertexStyle[mxConstants.STYLE_SPACING_TOP] = padding
        vertexStyle[mxConstants.STYLE_SPACING_BOTTOM] = padding * 6 / 10
        vertexStyle[mxConstants.STYLE_SPACING_LEFT] = padding * 10 / 9
        vertexStyle[mxConstants.STYLE_SPACING_RIGHT] = padding



        // Set color for all edges  (Light mode: dark gray,  Dark mode: medium gray)

        val edgeColor = JBColor(Gray._136, Gray._119)
        edgeStyle[mxConstants.STYLE_STROKECOLOR] = colorToHex(edgeColor)

        // Set other style parameters for all edges
        edgeStyle[mxConstants.STYLE_STROKEWIDTH] = 1.5
        edgeStyle[mxConstants.STYLE_ENDARROW] = mxConstants.ARROW_CLASSIC
        edgeStyle[mxConstants.STYLE_EDGE] = mxConstants.EDGESTYLE_ORTHOGONAL
        edgeStyle[mxConstants.STYLE_ROUNDED] = true
        edgeStyle[mxConstants.STYLE_ENDSIZE] = 6
    }

    /**
     * Populates the mxGraph model from a JGraphT graph.
     *
     * Each JGraphT vertex becomes an mxGraph vertex cell sized proportionally
     * to its label length, and each JGraphT edge becomes a directed mxGraph edge.
     */
    private fun populateGraph(jGraphTGraph: Graph<String, DefaultEdge>) {
        val parent = graph.defaultParent
        graph.model.beginUpdate()
        try {
            val cellMap = mutableMapOf<String, Any>()

            for (vertex in jGraphTGraph.vertexSet()) {
                val cell = graph.insertVertex(parent, null, vertex, 0.0, 0.0, 0.0, 0.0)
                graph.updateCellSize(cell)
                cellMap[vertex] = cell
            }

            for (edge in jGraphTGraph.edgeSet()) {
                val source = jGraphTGraph.getEdgeSource(edge)
                val target = jGraphTGraph.getEdgeTarget(edge)
                val sourceCell = cellMap[source]
                val targetCell = cellMap[target]
                if (sourceCell != null && targetCell != null) {
                    graph.insertEdge(parent, null, "", sourceCell, targetCell)
                }
            }
        } finally {
            graph.model.endUpdate()
        }
    }

    /**
     * Applies a hierarchical layout flowing top-to-bottom.
     *
     * Modules with no dependents appear at the top; leaf modules (those with no
     * dependencies of their own) appear at the bottom. This matches the natural
     * reading direction for a dependency graph.
     */
    private fun applyLayout() {
        val layout = mxHierarchicalLayout(graph, SwingConstants.NORTH)
        layout.interRankCellSpacing = JBUI.scale(50).toDouble()
        layout.intraCellSpacing = JBUI.scale(40).toDouble()
        layout.execute(graph.defaultParent)
    }

    /**
     * Scrolls the viewport so the graph content is centered on first display.
     *
     * The [addNotify] Swing hook is called when the panel is added to a
     * displayable container. [SwingUtilities.invokeLater] waits until after the
     * first layout pass, ensuring the window size is already known.
     * The flag prevents re-centering if the panel is ever re-parented.
     */
    override fun addNotify() {
        super.addNotify()
        if (!initialCenterDone) {
            initialCenterDone = true
            SwingUtilities.invokeLater(::centerGraph)
        }
    }

    /**
     * Scrolls the viewport so the graph content is centered in the visible area.
     *
     * Works by computing the midpoint of the slack between the content's preferred
     * size and the tool window's visible area, then setting that as the view
     * position. If the content is larger than the viewport in either dimension the
     * position is clamped to 0, so the graph never scrolls out of bounds.
     *
     * Note: this only produces perfect centering when the graph is larger than the
     * viewport. JScrollPane clamps the view position to non-negative values, so
     * centering smaller content requires a more involved approach, which is unnecessary.
     */
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
        /** Converts a [Color] to a "#RRGGBB" hex string as required by mxGraph style properties. */
        private fun colorToHex(color: Color): String =
            "#%02X%02X%02X".format(color.red, color.green, color.blue)
    }
}
