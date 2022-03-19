package io.github.toyota32k.boodroid.common

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Resources.Theme.getAttrColor(@AttrRes attrId:Int, @ColorInt def:Int=0): Int {
    val typedValue = TypedValue()
    return if(this.resolveAttribute(attrId, typedValue, true)) {
        return typedValue.data
    } else def
}

fun Resources.Theme.getAttrColorAsDrawable(@AttrRes attrId:Int): Drawable? {
    val typedValue = TypedValue()
    return if(this.resolveAttribute(attrId, typedValue, true)) {
        return ColorDrawable(typedValue.data)
    } else null
}

fun Resources.Theme.getAttrColorAsDrawable(@AttrRes attrId:Int, @ColorInt def:Int): Drawable {
    return ColorDrawable(getAttrColor(attrId, def))
}
