package app.openhearing.ui.hearingtest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.openhearing.R
import app.openhearing.audiogram.Audiogram
import app.openhearing.common.Ear
import kotlin.math.log2

// Audiology convention: right ear = red circles, left ear = blue crosses. The
// marker SHAPE identifies the ear (not just color), so the chart stays readable
// for colorblind users and in the high-contrast theme. Hues are chosen per
// light/dark surface for >= 3:1 contrast.
private val RightLight = Color(0xFFD32F2F)
private val LeftLight = Color(0xFF1976D2)
private val RightDark = Color(0xFFEF5350)
private val LeftDark = Color(0xFF2196F3)

// Standard audiogram level axis, hearing level in dB, quietest at the top.
private const val DB_MIN = -10.0
private const val DB_MAX = 90.0
private const val DB_GRID_STEP = 20

private data class EarSeries(val ear: Ear, val color: Color, val points: List<Pair<Double, Double>>)

/**
 * Audiogram-style plot of the hearing-check result: pitch (log-spaced) on the
 * x-axis, estimated threshold in dB HL on the inverted y-axis, one series per
 * ear. Purely visual — the detailed table remains the accessible data source
 * (the canvas carries a short content description pointing there).
 */
@Composable
fun AudiogramChart(audiogram: Audiogram, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val rightColor = if (dark) RightDark else RightLight
    val leftColor = if (dark) LeftDark else LeftLight
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle =
        MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val chartDescription = stringResource(R.string.check_chart_description)

    val frequencies =
        (audiogram.frequenciesFor(Ear.RIGHT) + audiogram.frequenciesFor(Ear.LEFT))
            .map { it.value }
            .distinct()
            .sorted()
    if (frequencies.size < 2) return

    val series =
        listOf(
            EarSeries(Ear.RIGHT, rightColor, earPoints(audiogram, Ear.RIGHT)),
            EarSeries(Ear.LEFT, leftColor, earPoints(audiogram, Ear.LEFT)),
        )

    Column(modifier = modifier) {
        Canvas(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .semantics { contentDescription = chartDescription },
        ) {
            drawChart(series, frequencies, gridColor, textMeasurer, labelStyle)
        }
        Legend(rightColor = rightColor, leftColor = leftColor)
    }
}

private fun DrawScope.drawChart(
    series: List<EarSeries>,
    frequencies: List<Double>,
    gridColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val left = 32.dp.toPx()
    val right = size.width - 12.dp.toPx()
    val top = 8.dp.toPx()
    val bottom = size.height - 20.dp.toPx()
    val logLo = log2(frequencies.first())
    val logHi = log2(frequencies.last())

    fun xOf(freq: Double): Float = left + ((log2(freq) - logLo) / (logHi - logLo)).toFloat() * (right - left)

    fun yOf(dbHl: Double): Float = top + ((dbHl - DB_MIN) / (DB_MAX - DB_MIN)).toFloat() * (bottom - top)

    // Recessive grid with dB labels down the left and pitch labels along the bottom.
    var db = 0
    while (db <= DB_MAX - DB_GRID_STEP / 2) {
        val y = yOf(db.toDouble())
        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        val label = textMeasurer.measure(db.toString(), labelStyle)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
        db += DB_GRID_STEP
    }
    frequencies.forEach { freq ->
        val x = xOf(freq)
        drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        val label = textMeasurer.measure(freqLabel(freq), labelStyle)
        drawText(label, topLeft = Offset(x - label.size.width / 2f, bottom + 4.dp.toPx()))
    }

    series.forEach { s ->
        val pts = s.points.map { (freq, dbHl) -> Offset(xOf(freq), yOf(dbHl)) }
        for (i in 0 until pts.size - 1) {
            drawLine(s.color, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
        }
        pts.forEach { p ->
            if (s.ear == Ear.RIGHT) circleMarker(p, s.color) else crossMarker(p, s.color)
        }
    }
}

@Composable
private fun Legend(rightColor: Color, leftColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Canvas(Modifier.size(16.dp)) { circleMarker(center, rightColor) }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chart_legend_right), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(20.dp))
        Canvas(Modifier.size(16.dp)) { crossMarker(center, leftColor) }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chart_legend_left), style = MaterialTheme.typography.labelMedium)
    }
}

private fun DrawScope.circleMarker(center: Offset, color: Color) {
    drawCircle(color, radius = 5.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
}

private fun DrawScope.crossMarker(center: Offset, color: Color) {
    val r = 5.dp.toPx()
    val w = 2.dp.toPx()
    drawLine(color, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), w)
    drawLine(color, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), w)
}

private fun earPoints(audiogram: Audiogram, ear: Ear): List<Pair<Double, Double>> =
    audiogram.frequenciesFor(ear).mapNotNull { freq ->
        audiogram.thresholdAt(ear, freq)?.let { freq.value to it.value }
    }

private fun freqLabel(freq: Double): String = if (freq >= 1000) "${(freq / 1000).toInt()}k" else "${freq.toInt()}"
