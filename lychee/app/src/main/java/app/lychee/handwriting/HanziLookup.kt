// SPDX-License-Identifier: LGPL-3.0-or-later
// Kotlin port of hanzi_lookup (https://github.com/gugray/hanzi_lookup, 01f90c3a, LGPL), itself
// derived from Jordan Kiang's HanziLookup. The stroke data (assets/lychee/mmah.bin, from Make Me
// a Hanzi via HanziLookupJS) is under the Arphic Public License: see handwriting/LICENSE-APL.
package app.lychee.handwriting

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline Chinese handwriting recognition for about 9,500 characters, used until (or instead of)
 * ML Kit's downloaded models. Strokes are lists of points in a 256 × 256 box.
 */
class HanziLookup private constructor(private val chars: List<CharData>) {

    class Point(val x: Int, val y: Int)
    class Match(val hanzi: String, val score: Float)

    private class CharData(val hanzi: String, val strokeCount: Int, val subStrokes: ByteArray) {
        val subStrokeCount get() = subStrokes.size / 3
    }

    private class SubStroke(val direction: Float, val length: Float, val centerX: Float, val centerY: Float)

    private val scoreMatrix = Array(MAX_SUB_STROKES + 1) { FloatArray(MAX_SUB_STROKES + 1) }
    private val directionScores = curveTable(CubicCurve(0f, 1f, 0.5f, 1f, 0.25f, -2f, 1f, 1f), 256)
    private val lengthScores = curveTable(CubicCurve(0f, 0f, 0.25f, 1f, 0.75f, 1f, 1f, 1f), 129)
    private val positionScores = FloatArray(450) { 1f - sqrt(it.toFloat()) / 22f }

    init {
        for (i in scoreMatrix.indices) {
            val penalty = -AVG_SUBSTROKE_LENGTH * SKIP_PENALTY * i
            scoreMatrix[i][0] = penalty
            scoreMatrix[0][i] = penalty
        }
    }

    /** The best [limit] characters for these strokes, best first. */
    @Synchronized
    fun lookup(strokes: List<List<Point>>, limit: Int): List<Match> {
        val usable = strokes.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return emptyList()
        val rect = boundingRect(usable)
        val analyzed = usable.map { subStrokes(it, pivots(it), rect) }
        val input = analyzed.flatten()
        val strokeCount = analyzed.size
        val subStrokeCount = input.size
        val strokeRange = strokesRange(strokeCount)
        val minStrokes = max(strokeCount - strokeRange, 1)
        val maxStrokes = min(strokeCount + strokeRange, MAX_STROKES)
        val subRange = subStrokesRange(subStrokeCount)
        val minSub = max(subStrokeCount - subRange, 1)
        val maxSub = min(subStrokeCount + subRange, MAX_SUB_STROKES)
        if (input.size > MAX_SUB_STROKES) return emptyList()

        val matches = ArrayList<Match>(limit + 1)
        for (c in chars) {
            if (c.strokeCount < minStrokes || c.strokeCount > maxStrokes) continue
            if (c.subStrokeCount < minSub || c.subStrokeCount > maxSub) continue
            var score = matchScore(input, subRange, c)
            if (strokeCount == c.strokeCount && strokeCount < CORRECT_STROKES_CAP) {
                val bonus = CORRECT_STROKES_BONUS * max(CORRECT_STROKES_CAP - strokeCount, 0) / CORRECT_STROKES_CAP
                score += bonus * score
            }
            file(matches, Match(c.hanzi, score), limit)
        }
        return matches
    }

    private fun file(matches: ArrayList<Match>, m: Match, limit: Int) {
        if (matches.size == limit && m.score <= matches.last().score) return
        val existing = matches.indexOfFirst { it.hanzi == m.hanzi }
        if (existing >= 0) {
            if (m.score <= matches[existing].score) return
            matches.removeAt(existing)
        }
        val at = matches.indexOfFirst { it.score < m.score }
        if (at >= 0) matches.add(at, m) else matches.add(m)
        if (matches.size > limit) matches.removeAt(matches.size - 1)
    }

    private fun matchScore(input: List<SubStroke>, range: Int, c: CharData): Float {
        val subs = c.subStrokes
        for (x in input.indices) {
            val inDir = input[x].direction.roundToInt()
            val inLength = input[x].length.roundToInt()
            val inCx = input[x].centerX.toInt()
            val inCy = input[x].centerY.toInt()
            for (y in 0 until c.subStrokeCount) {
                var newScore = -Float.MAX_VALUE
                if (abs(x - y) <= range) {
                    val cmpDir = subs[3 * y].toInt() and 0xff
                    val cmpLength = subs[3 * y + 1].toInt() and 0xff
                    val center = subs[3 * y + 2].toInt() and 0xff
                    val cmpCx = center shr 4
                    val cmpCy = center and 0x0f
                    val skip1 = scoreMatrix[x][y + 1] - inLength / 256f * SKIP_PENALTY
                    val skip2 = scoreMatrix[x + 1][y] - cmpLength / 256f * SKIP_PENALTY
                    val skip = max(skip1, skip2)
                    val match = subStrokeScore(inDir, inLength, cmpDir, cmpLength, inCx, inCy, cmpCx, cmpCy)
                    newScore = max(scoreMatrix[x][y] + match, skip)
                }
                scoreMatrix[x + 1][y + 1] = newScore
            }
        }
        return scoreMatrix[input.size][c.subStrokeCount]
    }

    private fun subStrokeScore(inDir: Int, inLength: Int, cmpDir: Int, cmpLength: Int, inCx: Int, inCy: Int, cmpCx: Int, cmpCy: Int): Float {
        var direction = directionScores[abs(inDir - cmpDir).coerceAtMost(255)]
        if (inLength < 64) {
            val bonusMax = min(1f, 1f - direction)
            direction += bonusMax * (1f - inLength / 64f)
        }
        val ratio = if (inLength > cmpLength) (cmpLength * 128f / inLength).roundToInt()
            else if (cmpLength == 0) 128 else (inLength * 128f / cmpLength).roundToInt()
        var score = lengthScores[ratio.coerceIn(0, 128)] * direction
        val dx = inCx - cmpCx
        val dy = inCy - cmpCy
        val closeness = positionScores[(dx * dx + dy * dy).coerceAtMost(449)]
        if (score > 0) score *= closeness else score /= closeness
        return score
    }

    private fun strokesRange(strokeCount: Int): Int {
        val curve = CubicCurve(0f, 0f, 0.35f, strokeCount * 0.4f, 0.6f, strokeCount.toFloat(), 1f, MAX_STROKES.toFloat())
        return curve.yOnCurve(curve.firstSolutionForX(LOOSENESS)).roundToInt()
    }

    private fun subStrokesRange(subStrokeCount: Int): Int {
        val y0 = subStrokeCount * 0.25f
        val ctrl1y = 1.5f * y0
        val curve = CubicCurve(0f, y0, 0.4f, ctrl1y, 0.75f, 1.5f * ctrl1y, 1f, MAX_SUB_STROKES.toFloat())
        return curve.yOnCurve(curve.firstSolutionForX(LOOSENESS)).roundToInt()
    }

    // --- input analysis ---

    private class Rect(val top: Float, val bottom: Float, val left: Float, val right: Float)

    private fun boundingRect(strokes: List<List<Point>>): Rect {
        var top = Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        var left = Float.MAX_VALUE; var right = -Float.MAX_VALUE
        for (s in strokes) for (p in s) {
            left = min(left, p.x.toFloat()); right = max(right, p.x.toFloat())
            top = min(top, p.y.toFloat()); bottom = max(bottom, p.y.toFloat())
        }
        if (top > 255f) top = 0f
        if (bottom < 0f) bottom = 255f
        if (left > 255f) left = 0f
        if (right < 0f) right = 255f
        return Rect(top, bottom, left, right)
    }

    private fun dist(a: Point, b: Point): Float {
        val dx = (a.x - b.x).toFloat()
        val dy = (a.y - b.y).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    private fun pivots(points: List<Point>): List<Int> {
        if (points.size < 2) return listOf(0)
        val markers = BooleanArray(points.size)
        var prev = 0
        var first = 0
        var pivot = 1
        markers[0] = true
        var local = dist(points[first], points[pivot])
        var running = local
        for (i in 2 until points.size) {
            val next = points[i]
            val pivotLength = dist(points[pivot], next)
            local += pivotLength
            running += pivotLength
            val fromPrevious = dist(points[prev], next)
            val fromFirst = dist(points[first], next)
            if (local > MAX_LOCAL_LENGTH_RATIO * fromPrevious || running > MAX_RUNNING_LENGTH_RATIO * fromFirst) {
                if (markers[prev] && dist(points[prev], points[pivot]) < MIN_SEGMENT_LENGTH) markers[prev] = false
                markers[pivot] = true
                running = pivotLength
                first = pivot
            }
            local = pivotLength
            prev = pivot
            pivot = i
        }
        markers[pivot] = true
        if (markers[prev] && dist(points[prev], points[pivot]) < MIN_SEGMENT_LENGTH && prev != 0) markers[prev] = false
        return markers.indices.filter { markers[it] }
    }

    private fun subStrokes(points: List<Point>, pivots: List<Int>, rect: Rect): List<SubStroke> {
        val result = ArrayList<SubStroke>()
        var prev = 0
        for (ix in pivots) {
            if (ix == prev) continue
            val a = points[prev]
            val b = points[ix]
            var direction = (PI - atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())).toFloat()
            direction = (direction * 256f / PI.toFloat() / 2f).roundToInt().toFloat()
            if (direction >= 256f) direction = 0f
            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            val dimSquared = if (width > height) width * width else height * height
            val normalizer = sqrt(dimSquared + dimSquared)
            val length = (min(dist(a, b) / normalizer, 1f) * 255f).roundToInt().toFloat()
            var x = (a.x + b.x) / 2f
            var y = (a.y + b.y) / 2f
            val side: Float
            if (width > height) {
                side = width
                x -= rect.left
                y = y - rect.top + (side - height) / 2f
            } else {
                side = height
                x = x - rect.left + (side - width) / 2f
                y -= rect.top
            }
            val cx = if (side > 0) x / side else 0.5f
            val cy = if (side > 0) y / side else 0.5f
            result.add(SubStroke(direction, length, (cx * 15f).roundToInt().toFloat(), (cy * 15f).roundToInt().toFloat()))
            prev = ix
        }
        return result
    }

    // --- curves ---

    private class CubicCurve(val x1: Float, val y1: Float, val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x2: Float, val y2: Float) {
        private val cx = 3f * (c1x - x1)
        private val bx = 3f * (c2x - c1x) - cx
        private val ax = x2 - x1 - bx - cx
        private val cy = 3f * (c1y - y1)
        private val by = 3f * (c2y - c1y) - cy
        private val ay = y2 - y1 - by - cy

        // Like the original, which uses powf (NaN for negative bases), only real cube roots of
        // non-negative numbers count.
        private fun pow3(v: Float) = v.toDouble().pow(1.0 / 3.0).toFloat()

        fun firstSolutionForX(x: Float): Float {
            val a = ax; val b = bx; val c = cx; val d = x1 - x
            val f = ((3f * c / a) - (b * b / (a * a))) / 3f
            val g = ((2f * b * b * b / (a * a * a)) - (9f * b * c / (a * a)) + (27f * d / a)) / 27f
            val h = (g * g / 4f) + (f * f * f / 27f)
            val roots = ArrayList<Float>(3)
            if (h > 0f) {
                val u = -g
                val r = u / 2f + sqrt(h)
                val s = pow3(r)
                val t = u / 2f - sqrt(h)
                val v = pow3(-t)
                roots.add((s - v) - b / (3f * a))
            } else if (f == 0f && g == 0f && h == 0f) {
                roots.add(-pow3(d / a))
            } else {
                val i = sqrt((g * g / 4f) - h)
                val j = pow3(i)
                val k = acos(-g / (2f * i))
                val l = -j
                val m = cos(k / 3f)
                val n = sqrt(3f) * sin(k / 3f)
                val p = -(b / (3f * a))
                roots.add(2f * j * cos(k / 3f) - b / (3f * a))
                roots.add(l * (m + n) + p)
                roots.add(l * (m - n) + p)
            }
            for (root in roots) {
                if (root >= -0.0000001f && root <= 1.0000001f) {
                    return root.coerceIn(0f, 1f)
                }
            }
            return Float.NaN
        }

        fun yOnCurve(t: Float) = ay * t * t * t + by * t * t + cy * t + y1
    }

    private fun curveTable(curve: CubicCurve, samples: Int): FloatArray {
        val table = FloatArray(samples)
        val inc = (curve.x2 - curve.x1) / samples
        var x = curve.x1
        for (i in 0 until samples) {
            table[i] = curve.yOnCurve(curve.firstSolutionForX(min(x, curve.x2)))
            x += inc
        }
        return table
    }

    companion object {
        private const val MAX_STROKES = 48
        private const val MAX_SUB_STROKES = 64
        private const val LOOSENESS = 0.15f
        private const val AVG_SUBSTROKE_LENGTH = 0.33f
        private const val SKIP_PENALTY = 1.75f
        private const val CORRECT_STROKES_BONUS = 0.1f
        private const val CORRECT_STROKES_CAP = 10
        private const val MIN_SEGMENT_LENGTH = 12.5f
        private const val MAX_LOCAL_LENGTH_RATIO = 1.1f
        private const val MAX_RUNNING_LENGTH_RATIO = 1.09f

        @Volatile private var instance: HanziLookup? = null

        fun get(context: Context): HanziLookup = instance ?: synchronized(this) {
            instance ?: HanziLookup(read(context.assets.open("lychee/mmah.bin").use { it.readBytes() })).also { instance = it }
        }

        /** For tests: load from the data file's bytes. */
        fun fromBytes(bytes: ByteArray) = HanziLookup(read(bytes))

        /** hanzi_lookup's data: bincode of [(char as UTF-8, stroke count u16, [(dir, length, center)])]. */
        private fun read(bytes: ByteArray): List<CharData> {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.long.toInt()
            val result = ArrayList<CharData>(count)
            repeat(count) {
                val lead = buf.get(buf.position()).toInt() and 0xff
                val n = when {
                    lead < 0x80 -> 1
                    lead < 0xE0 -> 2
                    lead < 0xF0 -> 3
                    else -> 4
                }
                val utf8 = ByteArray(n).also { buf.get(it) }
                val strokeCount = buf.short.toInt() and 0xffff
                val subs = ByteArray(buf.long.toInt() * 3).also { buf.get(it) }
                result.add(CharData(String(utf8, Charsets.UTF_8), strokeCount, subs))
            }
            return result
        }
    }
}
