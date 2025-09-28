package io.github.toyota32k.boodroid.data

import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.boodroid.R

//enum class Rating(val v:Int, @IdRes val id:Int) {
//    DREADFUL(1, R.id.tg_rating_dreadful),
//    BAD(2, R.id.tg_rating_bad),
//    NORMAL(3, R.id.tg_rating_normal),
//    GOOD(4, R.id.tg_rating_good),
//    EXCELLENT(5, R.id.tg_rating_excellent);
//
//    private class IDResolver : IIDValueResolver<Rating> {
//        override fun id2value(@IdRes id: Int):Rating  = Rating.id2value(id)
//        override fun value2id(v: Rating): Int = v.id
//    }
//
//    companion object {
//        fun id2value(@IdRes id: Int, def: Rating = NORMAL): Rating {
//            return values().find { it.id == id } ?: def
//        }
//        fun valueOf(v: Int, def: Rating = NORMAL): Rating {
//            return values().find { it.v == v } ?: def
//        }
//        val idResolver:IIDValueResolver<Rating> by lazy { IDResolver() }
//    }
//}
//
//enum class Mark(val v:Int, @IdRes val id:Int) {
//    NONE(0, View.NO_ID),
//    STAR(1, R.id.tg_mark_star),
//    FLAG(2, R.id.tg_mark_flag),
//    HEART(3, R.id.tg_mark_heart);
//
//    private class IDResolver : IIDValueResolver<Mark> {
//        override fun id2value(@IdRes id: Int): Mark = Mark.id2value(id)
//        override fun value2id(v: Mark): Int = v.id
//    }
//
//    companion object {
//        fun id2value(@IdRes id: Int, def: Mark = NONE): Mark {
//            return values().find { it.id == id } ?: def
//        }
//        fun valueOf(v: Int, def: Mark = NONE): Mark {
//            return values().find { it.v == v } ?: def
//        }
//        val idResolver:IIDValueResolver<Mark> by lazy { IDResolver() }
//    }
//}

enum class SourceType(@param:IdRes val v:Int, val id:Int) {
    DB(0, R.id.chk_src_db),
    LISTED(1, R.id.chk_src_listed),
    SELECTED(2, R.id.chk_src_selected);

    private class IDResolver : IIDValueResolver<SourceType> {
        override fun id2value(@IdRes id: Int): SourceType = SourceType.id2value(id)
        override fun value2id(v: SourceType): Int = v.id
    }

    companion object {
        fun id2value(@IdRes id: Int, def: SourceType = DB): SourceType {
            return entries.find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: SourceType = DB): SourceType {
            return entries.find { it.v == v } ?: def
        }

        val idResolver:IIDValueResolver<SourceType> by lazy { IDResolver() }
    }
}

object VideoItemFilter {

    private fun getQueryString(settings:Settings, date:Long, authToken:String?):String {
        val qb = QueryBuilder()
        if(authToken!=null) {
            qb.add("auth", authToken)
        }
        qb.add("f", "vap")    // Video|Audio
        if(settings.sourceType!=SourceType.DB) {
            qb.add("s", settings.sourceType.v)
        } else {
            val sos = settings.settingsOnActiveHost
            if (sos.minRating != 0) {
                qb.add("r", sos.minRating)
            }
            if (sos.marks.isNotEmpty()) {
                qb.add("m", sos.marks.joinToString(".") { "$it" })
            }
            if (sos.category.isNotEmpty() && sos.category != "All") {
                qb.add("c", sos.category)
            }
        }
        if(date>0) {
            qb.add("d","$date")
        }
        return qb.queryString
    }

    fun urlWithQueryString(settings: Settings, date:Long, authToken: String?) : String {
        val query = getQueryString(settings, date, authToken)
        return if(query.isNotEmpty()) {
            "${settings.baseUrl}list?${query}"
        } else {
            "${settings.baseUrl}list"
        }
    }

}