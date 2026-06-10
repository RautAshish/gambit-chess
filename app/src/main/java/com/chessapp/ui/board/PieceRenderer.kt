package com.chessapp.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.chessapp.domain.model.PieceType
import com.chessapp.domain.model.Color as PieceColor

/**
 * Draws chess pieces as scalable vector paths instead of Unicode glyphs. Unicode
 * chess characters render inconsistently across devices and dark pieces can vanish
 * on dark squares; vector shapes are crisp, identical everywhere, and always carry
 * a contrasting outline so they read on any square color.
 *
 * Pieces are authored on a 45x45 grid (the conventional chess-SVG viewbox) and
 * scaled to the cell. White pieces are light fill + dark outline; black pieces are
 * dark fill + light outline, so both pop on light and dark squares alike.
 */
object PieceRenderer {

    private const val VIEW = 45f

    fun draw(
        scope: DrawScope,
        type: PieceType,
        color: PieceColor,
        topLeft: Offset,
        cell: Float
    ) {
        val fill = if (color == PieceColor.WHITE) Color(0xFFF7F3E8) else Color(0xFF2B2B28)
        val line = if (color == PieceColor.WHITE) Color(0xFF1A1A17) else Color(0xFFE8E2D2)
        val scale = cell / VIEW
        // Inset slightly so pieces don't touch cell edges.
        val pad = cell * 0.07f
        val s = (cell - pad * 2) / VIEW

        scope.translate(topLeft.x + pad, topLeft.y + pad) {
            val path = pathFor(type).scaled(s)
            drawPath(path, color = fill, style = Fill)
            drawPath(path, color = line, style = Stroke(width = cell * 0.022f))
            // Internal detail strokes (crown points, cross, etc.)
            detailFor(type)?.scaled(s)?.let {
                drawPath(it, color = line, style = Stroke(width = cell * 0.018f))
            }
        }
    }

    // --- Path construction on the 45x45 grid ---

    private fun pathFor(type: PieceType): Path = when (type) {
        PieceType.PAWN -> pawn()
        PieceType.ROOK -> rook()
        PieceType.KNIGHT -> knight()
        PieceType.BISHOP -> bishop()
        PieceType.QUEEN -> queen()
        PieceType.KING -> king()
    }

    private fun detailFor(type: PieceType): Path? = when (type) {
        PieceType.BISHOP -> Path().apply { // slit in the mitre
            moveTo(22.5f, 12f); lineTo(22.5f, 19f)
            moveTo(19f, 15.5f); lineTo(26f, 15.5f)
        }
        else -> null
    }

    /** Common pedestal base used by most pieces. */
    private fun Path.base() {
        moveTo(9f, 40f)
        cubicTo(9f, 37f, 12f, 36f, 22.5f, 36f)
        cubicTo(33f, 36f, 36f, 37f, 36f, 40f)
        lineTo(36f, 42f)
        lineTo(9f, 42f)
        close()
    }

    private fun pawn(): Path = Path().apply {
        // head
        addOvalCompat(18f, 9f, 27f, 18f)
        // body (trapezoid + neck)
        moveTo(16f, 36f)
        cubicTo(16f, 28f, 19f, 24f, 19f, 20f)
        lineTo(26f, 20f)
        cubicTo(26f, 24f, 29f, 28f, 29f, 36f)
        close()
        base()
    }

    private fun rook(): Path = Path().apply {
        // crenellated top
        moveTo(12f, 8f); lineTo(16f, 8f); lineTo(16f, 11f)
        lineTo(20.5f, 11f); lineTo(20.5f, 8f); lineTo(24.5f, 8f)
        lineTo(24.5f, 11f); lineTo(29f, 11f); lineTo(29f, 8f)
        lineTo(33f, 8f); lineTo(33f, 15f); lineTo(30f, 18f)
        lineTo(30f, 32f); lineTo(33f, 36f); lineTo(12f, 36f)
        lineTo(15f, 32f); lineTo(15f, 18f); lineTo(12f, 15f)
        close()
        base()
    }

    private fun knight(): Path = Path().apply {
        // Staunton horse profile facing left
        moveTo(13f, 36f)
        lineTo(13f, 33f)
        cubicTo(13f, 28f, 16f, 25f, 20f, 23f)
        cubicTo(15f, 23f, 12f, 21f, 13f, 16f)
        lineTo(15f, 18f)
        cubicTo(16f, 12f, 20f, 8f, 25f, 9f)
        cubicTo(31f, 10f, 33f, 17f, 33f, 25f)
        cubicTo(33f, 31f, 32f, 34f, 31f, 36f)
        close()
        base()
    }

    private fun bishop(): Path = Path().apply {
        // mitre
        addOvalCompat(20.5f, 5f, 24.5f, 9f) // top knob
        moveTo(22.5f, 9f)
        cubicTo(28f, 12f, 30f, 20f, 27f, 28f)
        lineTo(18f, 28f)
        cubicTo(15f, 20f, 17f, 12f, 22.5f, 9f)
        close()
        // collar
        moveTo(16f, 32f)
        cubicTo(16f, 29f, 29f, 29f, 29f, 32f)
        lineTo(29f, 34f)
        cubicTo(29f, 36f, 16f, 36f, 16f, 34f)
        close()
        base()
    }

    private fun queen(): Path = Path().apply {
        // five-point crown
        moveTo(10f, 16f); lineTo(13f, 30f); lineTo(32f, 30f); lineTo(35f, 16f)
        lineTo(29.5f, 25f); lineTo(27f, 13f); lineTo(22.5f, 24f)
        lineTo(18f, 13f); lineTo(15.5f, 25f)
        close()
        // body below crown
        moveTo(13f, 30f)
        cubicTo(12f, 33f, 13f, 35f, 14f, 36f)
        lineTo(31f, 36f)
        cubicTo(32f, 35f, 33f, 33f, 32f, 30f)
        close()
        base()
    }

    private fun king(): Path = Path().apply {
        // bell-shaped body flaring from a neck up to the crown band
        moveTo(16f, 36f)
        cubicTo(13f, 30f, 14f, 24f, 18f, 22f)
        lineTo(27f, 22f)
        cubicTo(31f, 24f, 32f, 30f, 29f, 36f)
        close()
        // crown band
        moveTo(17f, 20f); lineTo(28f, 20f); lineTo(28f, 23f); lineTo(17f, 23f); close()
        // vertical cross bar
        moveTo(21f, 7f); lineTo(24f, 7f); lineTo(24f, 20f); lineTo(21f, 20f); close()
        // horizontal cross bar
        moveTo(18f, 11f); lineTo(27f, 11f); lineTo(27f, 14f); lineTo(18f, 14f); close()
        base()
    }

    // --- helpers ---

    private fun Path.addOvalCompat(l: Float, t: Float, r: Float, b: Float) {
        addOval(Rect(Offset(l, t), Size(r - l, b - t)))
    }

    private fun Path.scaled(factor: Float): Path {
        val m = android.graphics.Matrix().apply { setScale(factor, factor) }
        val androidPath = this.asAndroidPath()
        val out = android.graphics.Path(androidPath)
        out.transform(m)
        return out.asComposePath()
    }
}
