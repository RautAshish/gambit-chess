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
            moveTo(22.5f, 11f); lineTo(22.5f, 19f)
            moveTo(19.5f, 15f); lineTo(25.5f, 15f)
        }
        PieceType.KNIGHT -> Path().apply { // eye
            addOvalCompat(18.0f, 12.8f, 19.6f, 14.4f)
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
        addOvalCompat(18.5f, 8.5f, 26.5f, 16.5f)
        moveTo(18f, 19.5f)
        cubicTo(18f, 18.3f, 27f, 18.3f, 27f, 19.5f)
        lineTo(27f, 21f)
        cubicTo(27f, 22.2f, 18f, 22.2f, 18f, 21f)
        close()
        moveTo(19.5f, 22f)
        cubicTo(17.5f, 27f, 16.5f, 31f, 15f, 35.8f)
        lineTo(30f, 35.8f)
        cubicTo(28.5f, 31f, 27.5f, 27f, 25.5f, 22f)
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
        moveTo(14f, 35.8f)
        lineTo(14f, 33f)
        cubicTo(14f, 28f, 16.5f, 25.5f, 20.5f, 23.5f)
        cubicTo(17f, 23.5f, 13.5f, 22f, 13f, 18.5f)
        lineTo(11f, 16f)
        cubicTo(10.3f, 14.8f, 11.2f, 13.8f, 12.6f, 14.3f)
        lineTo(14.6f, 15.3f)
        cubicTo(14.6f, 12.6f, 16.2f, 10.2f, 18.6f, 8.8f)
        lineTo(19.6f, 5.6f)
        lineTo(21.8f, 8.6f)
        cubicTo(27f, 8.2f, 31f, 12f, 32.2f, 17.5f)
        cubicTo(33.4f, 23f, 33.2f, 29f, 32.2f, 35.8f)
        close()
        base()
    }

    private fun bishop(): Path = Path().apply {
        addOvalCompat(20.7f, 4.5f, 24.3f, 8.0f)
        moveTo(22.5f, 8.2f)
        cubicTo(27.5f, 10.5f, 29f, 16f, 27.5f, 22.5f)
        lineTo(17.5f, 22.5f)
        cubicTo(16f, 16f, 17.5f, 10.5f, 22.5f, 8.2f)
        close()
        moveTo(16.5f, 24.5f)
        cubicTo(16.5f, 23f, 28.5f, 23f, 28.5f, 24.5f)
        lineTo(28.5f, 26f)
        cubicTo(28.5f, 27.5f, 16.5f, 27.5f, 16.5f, 26f)
        close()
        moveTo(18.5f, 27.5f)
        cubicTo(17.5f, 30f, 16f, 33f, 14f, 35.8f)
        lineTo(31f, 35.8f)
        cubicTo(29f, 33f, 27.5f, 30f, 26.5f, 27.5f)
        close()
        base()
    }

    private fun queen(): Path = Path().apply {
        addOvalCompat(7.9f, 7.9f, 11.1f, 11.1f)
        addOvalCompat(14.4f, 5.4f, 17.6f, 8.6f)
        addOvalCompat(20.9f, 4.4f, 24.1f, 7.6f)
        addOvalCompat(27.4f, 5.4f, 30.6f, 8.6f)
        addOvalCompat(33.9f, 7.9f, 37.1f, 11.1f)
        moveTo(11f, 20f)
        lineTo(9.5f, 11f)
        lineTo(14.8f, 16.5f)
        lineTo(16f, 8.5f)
        lineTo(20.3f, 15.5f)
        lineTo(22.5f, 7.5f)
        lineTo(24.7f, 15.5f)
        lineTo(29f, 8.5f)
        lineTo(30.2f, 16.5f)
        lineTo(35.5f, 11f)
        lineTo(34f, 20f)
        close()
        moveTo(12f, 20f)
        cubicTo(12f, 18.6f, 33f, 18.6f, 33f, 20f)
        lineTo(33f, 22.5f)
        cubicTo(33f, 24f, 12f, 24f, 12f, 22.5f)
        close()
        moveTo(15f, 24f)
        cubicTo(13.8f, 27.5f, 14.2f, 31f, 13.2f, 35.8f)
        lineTo(31.8f, 35.8f)
        cubicTo(30.8f, 31f, 31.2f, 27.5f, 30f, 24f)
        close()
        base()
    }

    private fun king(): Path = Path().apply {
        moveTo(21.2f, 3.2f)
        lineTo(23.8f, 3.2f)
        lineTo(23.8f, 12.2f)
        lineTo(21.2f, 12.2f)
        close()
        moveTo(18f, 6.2f)
        lineTo(27f, 6.2f)
        lineTo(27f, 8.8f)
        lineTo(18f, 8.8f)
        close()
        moveTo(16.5f, 20f)
        cubicTo(16.5f, 12.5f, 28.5f, 12.5f, 28.5f, 20f)
        close()
        moveTo(14f, 20f)
        cubicTo(14f, 18.4f, 31f, 18.4f, 31f, 20f)
        lineTo(31f, 23f)
        cubicTo(31f, 24.6f, 14f, 24.6f, 14f, 23f)
        close()
        moveTo(15f, 24.5f)
        cubicTo(13.5f, 28f, 14f, 31.5f, 12.5f, 35.8f)
        lineTo(32.5f, 35.8f)
        cubicTo(31f, 31.5f, 31.5f, 28f, 30f, 24.5f)
        close()
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
