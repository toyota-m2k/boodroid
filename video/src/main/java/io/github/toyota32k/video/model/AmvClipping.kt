package io.github.toyota32k.video.model

import kotlin.math.max
import kotlin.math.min

data class AmvClipping (val start:Long, val end:Long=-1) {
    val isValidEnd  get() = end>start
    val isValidStart get() = start>0
    val isValid get() = start>0 || end>start
    val isEmpty get() = start==0L && end==-1L

    fun clipPos(pos:Long) : Long {
        return if(end>start) {
            min(max(start, pos), end)
        } else {
            max(start, pos)
        }
    }
    companion object {
        val empty: AmvClipping = AmvClipping(0L,-1L)

        fun make(start:Long, end:Long, range:Long): AmvClipping? {
            val rangedEnd = min(end, range)
            return if(rangedEnd>start) {
                AmvClipping(start, end)
            } else if(start>0) {
                AmvClipping(start)
            } else {
                null
            }
        }

        fun isValidClipping(start:Long, end:Long, range:Long):Boolean {
            val rangedEnd = min(end, range)
            return start>0 || min(end, range) > start
        }
    }
}
