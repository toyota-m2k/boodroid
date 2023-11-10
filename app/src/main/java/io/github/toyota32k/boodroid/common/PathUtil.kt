package io.github.toyota32k.boodroid.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.VectorDrawable
import android.graphics.drawable.shapes.PathShape
import androidx.annotation.ColorInt
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.dp2px

object PathUtil {
    val logger = UtLog("PathUtil")

    /**
     * Path文字列からDrawableを作る。
     * ShapeDrawable(PathShape(Path)) みたいな便利なDrawableがあったから、それを使ってみた。
     * ところが、MaterialButton にセットすると描画位置が、右下にズレてしまって、どうやったら真ん中に表示されるのかわからない。
     * 描画先によっては使えるのかもしれないが。。。
     */
    fun shapeDrawableFromPath(pathString:String, width:Float = 24f, height:Float = 24f): ShapeDrawable? {
        val path = createPathFromPathData(pathString) ?: return null
        return ShapeDrawable(PathShape(path, width, height))
    }

    /**
     * Path文字列からDrawableを作る。
     * 上で作成したShapeDrawableからビットマップを作り、それを持ったBitmapDrawableを返すようにした。
     * これで、MaterialButton上に、うまい具合に表示された。ちょっと重そうなので、キャッシュするなど工夫はしたほうがよいかも。
     */
    fun bitmapDrawableFromPath(
        context:Context,
        pathString:String,
        width:Float = 12f,
        height:Float = 12f,
        @ColorInt color:Int =Color.BLACK): BitmapDrawable? {
        val sd = shapeDrawableFromPath(pathString,width,height) ?: return null
        val px = context.dp2px(width)
        val py = context.dp2px(height)
        sd.shape.resize(px,py)
        val bitmap = Bitmap.createBitmap(px.toInt(), py.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        sd.paint.color = color
        sd.draw(canvas)
        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * @param pathData The string representing a path, the same as "d" string in svg file.
     * @return the generated Path object.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun createPathFromPathData(pathData: String): Path? {
        val path = Path().apply {fillType = Path.FillType.EVEN_ODD }
        val nodes = createNodesFromPathData(pathData)
        if (nodes != null) {
            try {
                PathDataNode.nodesToPath(nodes, path)
                return  path
            } catch (e: Throwable) {
                logger.error(e)
                //throw IllegalStateException("Error in parsing $pathData", e)
            }
        }
        return null
    }

    // Copy from Arrays.copyOfRange() which is only available from API level 9.
    /**
     * Copies elements from `original` into a new array, from indexes start (inclusive) to
     * end (exclusive). The original order of elements is preserved.
     * If `end` is greater than `original.length`, the result is padded
     * with the value `0.0f`.
     *
     * @param original the original array
     * @param start    the start index, inclusive
     * @param end      the end index, exclusive
     * @return the new array
     * @throws ArrayIndexOutOfBoundsException if `start < 0 || start > original.length`
     * @throws IllegalArgumentException       if `start > end`
     * @throws NullPointerException           if `original == null`
     */
    private fun copyOfRange(original: FloatArray, start: Int, end: Int): FloatArray {
        if (start > end) {
            throw IllegalArgumentException()
        }
        val originalLength = original.size
        if (start < 0 || start > originalLength) {
            throw ArrayIndexOutOfBoundsException()
        }
        val resultLength = end - start
        val copyLength = Math.min(resultLength, originalLength - start)
        val result = FloatArray(resultLength)
        System.arraycopy(original, start, result, 0, copyLength)
        return result
    }

    /**
     * @param pathData The string representing a path, the same as "d" string in svg file.
     * @return an array of the PathDataNode.
     */
    private fun createNodesFromPathData(pathData: String?): Array<PathDataNode>? {
        if (pathData.isNullOrBlank()) {
            return null
        }
        var start = 0
        var end = 1
        val list = ArrayList<PathDataNode>()
        while (end < pathData.length) {
            end = nextStart(pathData, end)
            val s = pathData.substring(start, end).trim { it <= ' ' }
            if (s.length > 0) {
                val `val` = getFloats(s)
                addNode(list, s[0], `val`)
            }
            start = end
            end++
        }
        if (end - start == 1 && start < pathData.length) {
            addNode(list, pathData[start], FloatArray(0))
        }
        return list.toTypedArray()
    }

    private fun nextStart(s: String, end_: Int): Int {
        var end = end_
        var c: Char
        while (end < s.length) {
            c = s[end]
            // Note that 'e' or 'E' are not valid path commands, but could be
            // used for floating point numbers' scientific notation.
            // Therefore, when searching for next command, we should ignore 'e'
            // and 'E'.
            if (((c.code - 'A'.code) * (c.code - 'Z'.code) <= 0 || (c.code - 'a'.code) * (c.code - 'z'.code) <= 0) && c != 'e' && c != 'E') {
                return end
            }
            end++
        }
        return end
    }

    private fun addNode(list: ArrayList<PathDataNode>, cmd: Char, `val`: FloatArray) {
        list.add(PathDataNode(cmd, `val`))
    }

    /**
     * Parse the floats in the string.
     * This is an optimized version of parseFloat(s.split(",|\\s"));
     *
     * @param s the string containing a command and list of floats
     * @return array of floats
     */
    private fun getFloats(s: String): FloatArray {
        if (s[0] == 'z' || s[0] == 'Z') {
            return FloatArray(0)
        }
        try {
            val results = FloatArray(s.length)
            var count = 0
            var startPosition = 1
            var endPosition: Int
            val result = ExtractFloatResult()
            val totalLength = s.length

            // The startPosition should always be the first character of the
            // current number, and endPosition is the character after the current
            // number.
            while (startPosition < totalLength) {
                extract(s, startPosition, result)
                endPosition = result.mEndPosition
                if (startPosition < endPosition) {
                    results[count++] =
                        s.substring(startPosition, endPosition).toFloat()
                }
                if (result.mEndWithNegOrDot) {
                    // Keep the '-' or '.' sign with next number.
                    startPosition = endPosition
                } else {
                    startPosition = endPosition + 1
                }
            }
            return copyOfRange(results, 0, count)
        } catch (e: NumberFormatException) {
            throw RuntimeException("error in parsing \"$s\"", e)
        }
    }

    /**
     * Calculate the position of the next comma or space or negative sign
     *
     * @param s      the string to search
     * @param start  the position to start searching
     * @param result the result of the extraction, including the position of the
     * the starting position of next number, whether it is ending with a '-'.
     */
    private fun extract(s: String, start: Int, result: ExtractFloatResult) {
        // Now looking for ' ', ',', '.' or '-' from the start.
        var currentIndex = start
        var foundSeparator = false
        result.mEndWithNegOrDot = false
        var secondDot = false
        var isExponential = false
        while (currentIndex < s.length) {
            val isPrevExponential = isExponential
            isExponential = false
            val currentChar = s[currentIndex]
            when (currentChar) {
                ' ', ',' -> foundSeparator = true
                '-' ->                     // The negative sign following a 'e' or 'E' is not a separator.
                    if (currentIndex != start && !isPrevExponential) {
                        foundSeparator = true
                        result.mEndWithNegOrDot = true
                    }

                '.' -> if (!secondDot) {
                    secondDot = true
                } else {
                    // This is the second dot, and it is considered as a separator.
                    foundSeparator = true
                    result.mEndWithNegOrDot = true
                }

                'e', 'E' -> isExponential = true
            }
            if (foundSeparator) {
                break
            }
            currentIndex++
        }
        // When there is nothing found, then we put the end position to the end
        // of the string.
        result.mEndPosition = currentIndex
    }


    private class ExtractFloatResult constructor() {
        // We need to return the position of the next separator and whether the
        // next float starts with a '-' or a '.'.
        var mEndPosition = 0
        var mEndWithNegOrDot = false
    }

    /**
     * Each PathDataNode represents one command in the "d" attribute of the svg
     * file.
     * An array of PathDataNode can represent the whole "d" attribute.
     */
    private class PathDataNode constructor(type: Char, params: FloatArray) {
        /**
         */
        var mType: Char = type

        /**
         */
        var mParams: FloatArray = params

        companion object {
            /**
             * Convert an array of PathDataNode to Path.
             *
             * @param node The source array of PathDataNode.
             * @param path The target Path object.
             */
            fun nodesToPath(node: Array<PathDataNode>, path: Path) {
                val current = FloatArray(6)
                var previousCommand = 'm'
                for (i in node.indices) {
                    addCommand(path, current, previousCommand, node[i].mType, node[i].mParams)
                    previousCommand = node[i].mType
                }
            }

            private fun addCommand(
                path: Path, current: FloatArray,
                previousCmd_: Char, cmd: Char, value: FloatArray
            ) {
                var previousCmd = previousCmd_
                var incr = 2
                var currentX = current[0]
                var currentY = current[1]
                var ctrlPointX = current[2]
                var ctrlPointY = current[3]
                var currentSegmentStartX = current[4]
                var currentSegmentStartY = current[5]
                var reflectiveCtrlPointX: Float
                var reflectiveCtrlPointY: Float
                when (cmd) {
                    'z', 'Z' -> {
                        path.close()
                        // Path is closed here, but we need to move the pen to the
                        // closed position. So we cache the segment's starting position,
                        // and restore it here.
                        currentX = currentSegmentStartX
                        currentY = currentSegmentStartY
                        ctrlPointX = currentSegmentStartX
                        ctrlPointY = currentSegmentStartY
                        path.moveTo(currentX, currentY)
                    }

                    'm', 'M', 'l', 'L', 't', 'T' -> incr = 2
                    'h', 'H', 'v', 'V' -> incr = 1
                    'c', 'C' -> incr = 6
                    's', 'S', 'q', 'Q' -> incr = 4
                    'a', 'A' -> incr = 7
                }
                var k = 0
                while (k < value.size) {
                    when (cmd) {
                        'M', 'm' -> {
                            currentX = value[k + 0]
                            currentY = value[k + 1]
                            if (k > 0) {
                                // According to the spec, if a moveto is followed by multiple
                                // pairs of coordinates, the subsequent pairs are treated as
                                // implicit lineto commands.
                                path.lineTo(value[k + 0], value[k + 1])
                            } else {
                                path.moveTo(value[k + 0], value[k + 1])
                                currentSegmentStartX = currentX
                                currentSegmentStartY = currentY
                            }
                        }

                        'L','l' -> {
                            path.lineTo(value[k + 0], value[k + 1])
                            currentX = value[k + 0]
                            currentY = value[k + 1]
                        }

                        'H','h' -> {
                            path.lineTo(value[k + 0], currentY)
                            currentX = value[k + 0]
                        }

                        'V','v' -> {
                            path.lineTo(currentX, value[k + 0])
                            currentY = value[k + 0]
                        }

                        'C','c' -> {
                            path.cubicTo(
                                value[k + 0], value[k + 1], value[k + 2], value[k + 3],
                                value[k + 4], value[k + 5]
                            )
                            currentX = value[k + 4]
                            currentY = value[k + 5]
                            ctrlPointX = value[k + 2]
                            ctrlPointY = value[k + 3]
                        }

                        'S','s' -> {
                            reflectiveCtrlPointX = currentX
                            reflectiveCtrlPointY = currentY
                            if ((previousCmd == 'c') || (previousCmd == 's'
                                        ) || (previousCmd == 'C') || (previousCmd == 'S')
                            ) {
                                reflectiveCtrlPointX = 2 * currentX - ctrlPointX
                                reflectiveCtrlPointY = 2 * currentY - ctrlPointY
                            }
                            path.cubicTo(
                                reflectiveCtrlPointX, reflectiveCtrlPointY,
                                value[k + 0], value[k + 1], value[k + 2], value[k + 3]
                            )
                            ctrlPointX = value[k + 0]
                            ctrlPointY = value[k + 1]
                            currentX = value[k + 2]
                            currentY = value[k + 3]
                        }

                        'Q','q' -> {
                            path.quadTo(value[k + 0], value[k + 1], value[k + 2], value[k + 3])
                            ctrlPointX = value[k + 0]
                            ctrlPointY = value[k + 1]
                            currentX = value[k + 2]
                            currentY = value[k + 3]
                        }

                        'T','t' -> {
                            reflectiveCtrlPointX = currentX
                            reflectiveCtrlPointY = currentY
                            if ((previousCmd == 'q') || (previousCmd == 't'
                                        ) || (previousCmd == 'Q') || (previousCmd == 'T')
                            ) {
                                reflectiveCtrlPointX = 2 * currentX - ctrlPointX
                                reflectiveCtrlPointY = 2 * currentY - ctrlPointY
                            }
                            path.quadTo(
                                reflectiveCtrlPointX, reflectiveCtrlPointY,
                                value[k + 0], value[k + 1]
                            )
                            ctrlPointX = reflectiveCtrlPointX
                            ctrlPointY = reflectiveCtrlPointY
                            currentX = value[k + 0]
                            currentY = value[k + 1]
                        }

                        'A', 'a' -> {
                            drawArc(
                                path,
                                currentX,
                                currentY,
                                value[k + 5],
                                value[k + 6],
                                value[k + 0],
                                value[k + 1],
                                value[k + 2],
                                value[k + 3] != 0f,
                                value[k + 4] != 0f
                            )
                            currentX = value[k + 5]
                            currentY = value[k + 6]
                            ctrlPointX = currentX
                            ctrlPointY = currentY
                        }
                    }
                    previousCmd = cmd
                    k += incr
                }
                current[0] = currentX
                current[1] = currentY
                current[2] = ctrlPointX
                current[3] = ctrlPointY
                current[4] = currentSegmentStartX
                current[5] = currentSegmentStartY
            }

            private fun drawArc(
                p: Path,
                x0: Float,
                y0: Float,
                x1: Float,
                y1: Float,
                a: Float,
                b: Float,
                theta: Float,
                isMoreThanHalf: Boolean,
                isPositiveArc: Boolean
            ) {

                /* Convert rotation angle from degrees to radians */
                val thetaD = Math.toRadians(theta.toDouble())
                /* Pre-compute rotation matrix entries */
                val cosTheta = Math.cos(thetaD)
                val sinTheta = Math.sin(thetaD)
                /* Transform (x0, y0) and (x1, y1) into unit space */
                /* using (inverse) rotation, followed by (inverse) scale */
                val x0p = (x0 * cosTheta + y0 * sinTheta) / a
                val y0p = (-x0 * sinTheta + y0 * cosTheta) / b
                val x1p = (x1 * cosTheta + y1 * sinTheta) / a
                val y1p = (-x1 * sinTheta + y1 * cosTheta) / b

                /* Compute differences and averages */
                val dx = x0p - x1p
                val dy = y0p - y1p
                val xm = (x0p + x1p) / 2
                val ym = (y0p + y1p) / 2
                /* Solve for intersecting unit circles */
                val dsq = dx * dx + dy * dy
                if (dsq == 0.0) {
                    logger.warn(" Points are coincident")
                    return  /* Points are coincident */
                }
                val disc = 1.0 / dsq - 1.0 / 4.0
                if (disc < 0.0) {
                    logger.warn("Points are too far apart $dsq")
                    val adjust = (Math.sqrt(dsq) / 1.99999).toFloat()
                    drawArc(
                        p, x0, y0, x1, y1, a * adjust,
                        b * adjust, theta, isMoreThanHalf, isPositiveArc
                    )
                    return  /* Points are too far apart */
                }
                val s = Math.sqrt(disc)
                val sdx = s * dx
                val sdy = s * dy
                var cx: Double
                var cy: Double
                if (isMoreThanHalf == isPositiveArc) {
                    cx = xm - sdy
                    cy = ym + sdx
                } else {
                    cx = xm + sdy
                    cy = ym - sdx
                }
                val eta0 = Math.atan2((y0p - cy), (x0p - cx))
                val eta1 = Math.atan2((y1p - cy), (x1p - cx))
                var sweep = (eta1 - eta0)
                if (isPositiveArc != (sweep >= 0)) {
                    if (sweep > 0) {
                        sweep -= 2 * Math.PI
                    } else {
                        sweep += 2 * Math.PI
                    }
                }
                cx *= a.toDouble()
                cy *= b.toDouble()
                val tcx = cx
                cx = cx * cosTheta - cy * sinTheta
                cy = tcx * sinTheta + cy * cosTheta
                arcToBezier(
                    p,
                    cx,
                    cy,
                    a.toDouble(),
                    b.toDouble(),
                    x0.toDouble(),
                    y0.toDouble(),
                    thetaD,
                    eta0,
                    sweep
                )
            }

            /**
             * Converts an arc to cubic Bezier segments and records them in p.
             *
             * @param p     The target for the cubic Bezier segments
             * @param cx    The x coordinate center of the ellipse
             * @param cy    The y coordinate center of the ellipse
             * @param a     The radius of the ellipse in the horizontal direction
             * @param b     The radius of the ellipse in the vertical direction
             * @param e1x_   E(eta1) x coordinate of the starting point of the arc
             * @param e1y_   E(eta2) y coordinate of the starting point of the arc
             * @param theta The angle that the ellipse bounding rectangle makes with horizontal plane
             * @param start The start angle of the arc on the ellipse
             * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
             */
            private fun arcToBezier(
                p: Path,
                cx: Double,
                cy: Double,
                a: Double,
                b: Double,
                e1x_: Double,
                e1y_: Double,
                theta: Double,
                start: Double,
                sweep: Double
            ) {
                // Taken from equations at: http://spaceroots.org/documents/ellipse/node8.html
                // and http://www.spaceroots.org/documents/ellipse/node22.html

                // Maximum of 45 degrees per cubic Bezier segment
                var e1x = e1x_
                var e1y = e1y_
                val numSegments = Math.ceil(Math.abs(sweep * 4 / Math.PI)).toInt()
                var eta1 = start
                val cosTheta = Math.cos(theta)
                val sinTheta = Math.sin(theta)
                val cosEta1 = Math.cos(eta1)
                val sinEta1 = Math.sin(eta1)
                var ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1)
                var ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1)
                val anglePerSegment = sweep / numSegments
                for (i in 0 until numSegments) {
                    val eta2 = eta1 + anglePerSegment
                    val sinEta2 = Math.sin(eta2)
                    val cosEta2 = Math.cos(eta2)
                    val e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2)
                    val e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2)
                    val ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2
                    val ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2
                    val tanDiff2 = Math.tan((eta2 - eta1) / 2)
                    val alpha =
                        Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3
                    val q1x = e1x + alpha * ep1x
                    val q1y = e1y + alpha * ep1y
                    val q2x = e2x - alpha * ep2x
                    val q2y = e2y - alpha * ep2y

                    // Adding this no-op call to workaround a proguard related issue.
                    p.rLineTo(0f, 0f)
                    p.cubicTo(
                        q1x.toFloat(),
                        q1y.toFloat(),
                        q2x.toFloat(),
                        q2y.toFloat(),
                        e2x.toFloat(),
                        e2y.toFloat()
                    )
                    eta1 = eta2
                    e1x = e2x
                    e1y = e2y
                    ep1x = ep2x
                    ep1y = ep2y
                }
            }
        }
    }
}
