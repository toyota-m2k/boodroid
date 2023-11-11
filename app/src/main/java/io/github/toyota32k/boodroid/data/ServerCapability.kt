package io.github.toyota32k.boodroid.data

import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

interface ICapability {
    val hostAddress:String
    val baseUrl : String

    val serverName:String
    val version:Int                // protocol version
    val root:String
    val hasCategory:Boolean
    val hasRating:Boolean
    val hasMark:Boolean
    val hasChapter:Boolean
    val reputation:Int          // 0:なし, 1:RO, 2:RW
    val diff:Boolean
    val sync:Boolean
    val acceptRequest:Boolean
    val hasView:Boolean
    val needAuth:Boolean
    val canGetReputation:Boolean get() = reputation>0
    val canPutReputation:Boolean get() = reputation>1
}
data class Capability(
    override val hostAddress: String,
    override val serverName:String,
    override val version:Int,                // protocol version
    override val root:String,
    override val hasCategory:Boolean,
    override val hasRating:Boolean,
    override val hasMark:Boolean,
    override val hasChapter:Boolean,
    override val reputation:Int,
    override val diff:Boolean,
    override val sync:Boolean,
    override val acceptRequest:Boolean,
    override val hasView:Boolean,
    override val needAuth:Boolean,
) : ICapability {
    constructor(hostAddress:String, j:JSONObject) : this(
        hostAddress,
        serverName = j.optString("serverName", "unknown"),
        version = j.optInt("version", 0),
        root = j.optString("root", "/ytplayer/"),
        hasCategory = j.optBoolean("category", false),
        hasRating = j.optBoolean("rating", false),
        hasMark = j.optBoolean("mark", false),
        hasChapter = j.optBoolean("chapter", false),
        reputation = j.optInt("reputation", 0),
        diff = j.optBoolean("diff", false),
        sync = j.optBoolean("sync", false),
        acceptRequest = j.optBoolean("acceptRequest", false),
        hasView = j.optBoolean("hasView", false),
        needAuth = j.optBoolean("authentication", false),
    )

    override val baseUrl : String get() = "http://${hostAddress}${root}"

    companion object {
        val empty = Capability(
            hostAddress = "",
            serverName = "unknown",
            version = 0,
            root = "/",
            hasCategory = false,
            hasRating = false,
            hasMark = false,
            hasChapter = false,
            reputation = 0,
            diff = false,
            sync = false,
            acceptRequest = false,
            hasView = false,
            needAuth = false
        )
        suspend fun get(hostAddress: String):Capability? {
//            if (!AppViewModel.instance.settings.isValid) return empty
            return withContext(Dispatchers.IO) {
                val url = "http://${hostAddress}/capability"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                try {
                    Capability(hostAddress, NetClient.executeAndGetJsonAsync(req))
                } catch (e: Throwable) {
                    UtLogger.stackTrace(e)
                    null
                }
            }
        }
    }
}

data class ServerCapability(private val capability: Capability, val categoryList: CategoryList, val markList:MarkList, val ratingList:RatingList) : ICapability by capability {
    companion object {
        val empty:ServerCapability = ServerCapability(Capability.empty, CategoryList.emptyList, MarkList.emptyList, RatingList.emptyList)
        suspend fun get(hostAddress:String?): ServerCapability? {
            if(hostAddress==null) return null
            val capability = Capability.get(hostAddress) ?: return null
            return ServerCapability(
                capability,
                CategoryList.getCategoryList(capability),
                MarkList.getMarkList(capability),
                RatingList.getRatingList(capability)
                )
        }
    }

}