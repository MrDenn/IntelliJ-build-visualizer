package io.github.mrdenn.intellijbuildvisualizer

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
import javax.swing.JPanel
import javax.swing.SwingConstants

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

    init {
        configureStyles()
        populateGraph(jGraphTGraph)
        applyLayout()

        graphComponent = mxGraphComponent(graph).apply {
            isConnectable = false
            isPanning = true
            setDragEnabled(false)
            setToolTips(true)

            viewport.isOpaque = true
            viewport.background = UIUtil.getPanelBackground()
            background = UIUtil.getPanelBackground()
        }

        // Turn most user interactions with the graph off
        graph.isCellsEditable = false
        graph.isCellsResizable = false
        graph.isCellsDeletable = false

        // Allow user to move cells (for now)
        // Edges also remain moveable to accommodate for this
        graph.isCellsMovable = true

        add(graphComponent, BorderLayout.CENTER)
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
                val width = JBUI.scale(20 + vertex.length * 8).toDouble()
                val height = JBUI.scale(36).toDouble()
                val cell = graph.insertVertex(parent, null, vertex, 0.0, 0.0, width, height)
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

    companion object {
        /** Converts a [Color] to a "#RRGGBB" hex string as required by mxGraph style properties. */
        private fun colorToHex(color: Color): String =
            "#%02X%02X%02X".format(color.red, color.green, color.blue)
    }
}
