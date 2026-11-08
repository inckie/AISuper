package com.damn.aisuper.layout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

@Composable
fun TableLayout(
    widget: TableWidget,
    values: Map<String, JsonElement>,
    styleSheet: StyleSheet?,
    modifier: Modifier = Modifier,
    renderCell: @Composable (Widget, Modifier) -> Unit
) {
    val visible = widget.visibilityId?.let { values[it]?.booleanOrNull() } ?: true
    if (!visible) return

    val style = resolveStyleRule(widget, styleSheet)

    // Gather all visible rows
    val allRows: List<Widget> = buildList {
        addAll(widget.effectiveRows)
        val dynamicId = widget.effectiveDynamicId
        if (dynamicId != null) {
            addAll(resolveDynamicWidgets(values[dynamicId]))
        }
    }.filter { row ->
        row.visibilityId?.let { values[it]?.booleanOrNull() } ?: true
    }

    if (allRows.isEmpty()) {
        Spacer(modifier.then(widget.layoutModifier()).applyStyleRule(style))
        return
    }

    // Extract visible cells per row
    val grid: List<List<Widget>> = allRows.map { row ->
        when (row) {
            is TableRowWidget -> row.children
            is RowWidget -> row.children
            else -> listOf(row)
        }.filter { cell ->
            cell.visibilityId?.let { values[it]?.booleanOrNull() } ?: true
        }
    }

    val numRows = grid.size
    val contentCols = grid.maxOfOrNull { it.size } ?: 0
    val numCols = maxOf(contentCols, widget.columns.size)

    if (numRows == 0 || numCols == 0) {
        Spacer(modifier.then(widget.layoutModifier()).applyStyleRule(style))
        return
    }

    var tableModifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
    if (widget.isScrollable) {
        val dir = widget.scrollDirection?.lowercase() ?: "horizontal"
        if (dir == "horizontal" || dir == "both") {
            tableModifier = tableModifier.horizontalScroll(rememberScrollState())
        }
        if (dir == "vertical" || dir == "both") {
            tableModifier = tableModifier.verticalScroll(rememberScrollState())
        }
    }

    Layout(
        content = {
            for (r in 0 until numRows) {
                val rowWidget = allRows.getOrNull(r)
                val rowStyle = if (rowWidget != null && rowWidget.classes.isNotEmpty()) {
                    resolveStyleRule(rowWidget, styleSheet)
                } else null
                val rowModifier = if (rowStyle != null) Modifier.applyStyleRule(rowStyle) else Modifier

                val rowCells = grid[r]
                for (c in 0 until numCols) {
                    val cell = rowCells.getOrNull(c)
                    if (cell != null) {
                        renderCell(cell, rowModifier)
                    } else {
                        Spacer(rowModifier)
                    }
                }
            }
        },
        modifier = tableModifier
    ) { measurables, constraints ->
        val maxIntrinsicColWidth = IntArray(numCols)
        val colWeights = FloatArray(numCols) { -1f }
        val fixedColWidths = IntArray(numCols) { -1 }

        for (c in 0 until numCols) {
            val colDef = widget.columns.getOrNull(c)
            val colWidthVal = colDef?.width
            if (colWidthVal != null) {
                fixedColWidths[c] = colWidthVal.dp.roundToPx()
                maxIntrinsicColWidth[c] = fixedColWidths[c]
            } else {
                var maxW = 0
                for (r in 0 until numRows) {
                    val m = measurables[r * numCols + c]
                    val w = m.maxIntrinsicWidth(constraints.maxHeight)
                    if (w > maxW) maxW = w
                }
                maxIntrinsicColWidth[c] = maxW
            }

            val weight = colDef?.weight ?: (0 until numRows).firstNotNullOfOrNull { r ->
                grid[r].getOrNull(c)?.weight
            }
            if (weight != null && weight > 0f) {
                colWeights[c] = weight
            }
        }

        val colWidths = IntArray(numCols)
        val totalWeight = (0 until numCols).sumOf { c ->
            if (fixedColWidths[c] < 0 && colWeights[c] > 0f) colWeights[c].toDouble() else 0.0
        }.toFloat()

        val hasWeightedCols = totalWeight > 0f

        if (constraints.hasBoundedWidth && hasWeightedCols) {
            val fixedAndIntrinsicSum = (0 until numCols).sumOf { c ->
                if (fixedColWidths[c] >= 0) fixedColWidths[c]
                else if (colWeights[c] <= 0f) maxIntrinsicColWidth[c]
                else 0
            }
            val availableForWeighted = (constraints.maxWidth - fixedAndIntrinsicSum).coerceAtLeast(0)
            var assignedWeightWidth = 0
            val lastWeightedIndex = (numCols - 1 downTo 0).firstOrNull { c ->
                fixedColWidths[c] < 0 && colWeights[c] > 0f
            }

            for (c in 0 until numCols) {
                if (fixedColWidths[c] >= 0) {
                    colWidths[c] = fixedColWidths[c]
                } else if (colWeights[c] > 0f) {
                    if (c == lastWeightedIndex) {
                        colWidths[c] = (availableForWeighted - assignedWeightWidth).coerceAtLeast(0)
                    } else {
                        val w = ((colWeights[c] / totalWeight) * availableForWeighted).roundToInt()
                        colWidths[c] = w
                        assignedWeightWidth += w
                    }
                } else {
                    colWidths[c] = maxIntrinsicColWidth[c]
                }
            }
        } else {
            for (c in 0 until numCols) {
                colWidths[c] = if (fixedColWidths[c] >= 0) fixedColWidths[c] else maxIntrinsicColWidth[c]
            }
        }

        val placeables = Array(numRows) { r ->
            Array(numCols) { c ->
                val m = measurables[r * numCols + c]
                val cellConstraints = Constraints(
                    minWidth = colWidths[c],
                    maxWidth = colWidths[c],
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
                m.measure(cellConstraints)
            }
        }

        val rowHeights = IntArray(numRows) { r ->
            (0 until numCols).maxOfOrNull { c -> placeables[r][c].height } ?: 0
        }

        val totalTableWidth = colWidths.sum().coerceIn(constraints.minWidth, constraints.maxWidth)
        val totalTableHeight = rowHeights.sum().coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalTableWidth, totalTableHeight) {
            var y = 0
            for (r in 0 until numRows) {
                var x = 0
                for (c in 0 until numCols) {
                    val p = placeables[r][c]
                    p.placeRelative(x, y)
                    x += colWidths[c]
                }
                y += rowHeights[r]
            }
        }
    }
}
