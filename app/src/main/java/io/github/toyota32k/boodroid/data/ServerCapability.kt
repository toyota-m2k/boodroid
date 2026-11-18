package io.github.toyota32k.boodroid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

enum class ReputationSupport(val intValue:Int) {
    No(0),
    ReadOnly(1),
    ReadWrite(2),
    ;
    val readable: Boolean get() = intValue>0
    val writable: Boolean get() = intValue>1

    companion object {
        fun fromValue(v:Int): ReputationSupport {
            return entries.firstOrNull { it.intValue==v } ?: No
        }
    }
}

interface ICapability {
    val hostAddress:String
    val httpsOnly: Boolean
    val baseUrl : String

    val serverName:String
    val version:Int                // protocol version
    val root:String
    val supportCategory:ReputationSupport
    val supportRating:ReputationSupport
    val supportMark:ReputationSupport
    val hasChapter:Boolean
//    val reputation:Int          // 0:なし, 1:RO, 2:RW
    val diff:Boolean
    val sync:Boolean
    val acceptRequest:Boolean
    val hasView:Boolean
    val needAuth:Boolean
    val supportedTypes: String     // v/a/p
    val canExtractAudio:Boolean get() = supportedTypes.contains("x")

    val canGetReputation:Boolean get() {
        return arrayOf(supportCategory, supportRating, supportMark).any {it.intValue>0 }
    }

    val canPutReputation:Boolean get() {
        return arrayOf(supportCategory, supportRating, supportMark).any {it.intValue>1 }
    }

}

data class Capability(
    override val hostAddress: String,
    /** true なら baseUrl で https スキームを使う */
    override val httpsOnly: Boolean,
    override val serverName:String,
    override val version:Int,                // protocol version
    override val root:String,
    override val supportCategory: ReputationSupport,
    override val supportRating: ReputationSupport,
    override val supportMark: ReputationSupport,
    override val hasChapter:Boolean,
//    override val reputation:Int,
    override val diff:Boolean,
    override val sync:Boolean,
    override val acceptRequest:Boolean,
    override val hasView:Boolean,
    override val needAuth:Boolean,
    override val supportedTypes: String
) : ICapability {

    override val baseUrl : String get() = "${if (httpsOnly) "https" else "http"}://${hostAddress}${root}"

    companion object {
        fun fromJson(hostAddress:String, httpsOnly:Boolean, j:JSONObject): ICapability {
            val version = j.optInt("version", 0)
            return if (version == 3) {
                Capability(
                    hostAddress,
                    httpsOnly = httpsOnly,
                    serverName = j.optString("serverName", "unknown"),
                    version = j.optInt("version", 0),
                    root = j.optString("root", "/ytplayer/"),
                    supportCategory = ReputationSupport.fromValue(j.optInt("category", 0)),
                    supportRating = ReputationSupport.fromValue(j.optInt("rating", 0)),
                    supportMark = ReputationSupport.fromValue(j.optInt("mark", 0)),
                    hasChapter = j.optBoolean("chapter", false),
                    diff = j.optBoolean("diff", false),
                    sync = j.optBoolean("sync", false),
                    acceptRequest = j.optBoolean("acceptRequest", false),
                    hasView = j.optBoolean("hasView", false),
                    needAuth = j.optBoolean("authentication", false),
                    supportedTypes  = j.optString("types", "")
                )
            } else {
                val reputation = j.optInt("reputation", 0)
                Capability(
                    hostAddress,
                    httpsOnly = httpsOnly,
                    serverName = j.optString("serverName", "unknown"),
                    version = j.optInt("version", 0),
                    root = j.optString("root", "/ytplayer/"),
                    supportCategory = ReputationSupport.fromValue(reputation),
                    supportRating = ReputationSupport.fromValue(reputation),
                    supportMark = ReputationSupport.fromValue(reputation),
                    hasChapter = j.optBoolean("chapter", false),
                    diff = j.optBoolean("diff", false),
                    sync = j.optBoolean("sync", false),
                    acceptRequest = j.optBoolean("acceptRequest", false),
                    hasView = j.optBoolean("hasView", false),
                    needAuth = j.optBoolean("authentication", false),
                    supportedTypes = j.optString("types", "")
                )
            }
        }

        val empty = Capability(
            hostAddress = "",
            httpsOnly = false,
            serverName = "unknown",
            version = 0,
            root = "/",
            supportCategory = ReputationSupport.No,
            supportRating = ReputationSupport.No,
            supportMark = ReputationSupport.No,
            hasChapter = false,
            diff = false,
            sync = false,
            acceptRequest = false,
            hasView = false,
            needAuth = false,
            supportedTypes = "va"
        )
        suspend fun get(hostAddress: String, httpsOnly: Boolean = false):ICapability? {
            return withContext(Dispatchers.IO) {
                val scheme = if (httpsOnly) "https" else "http"
                val url = "${scheme}://${hostAddress}/capability?v=3"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                try {
                    Capability.fromJson(hostAddress, httpsOnly, NetClient.executeAndGetJsonAsync(req))
                } catch (e: Throwable) {
                    NetClient.logger.stackTrace(e)
                    null
                }
            }
        }
    }
}

data class ServerCapability(private val capability: ICapability, val categoryList: CategoryList, val markList:MarkList, val ratingList:RatingList) : ICapability by capability {
    companion object {
        val empty:ServerCapability = ServerCapability(Capability.empty, CategoryList.emptyList, MarkList.emptyList, RatingList.emptyList)
        suspend fun get(hostAddress:String?, httpsOnly: Boolean = false): ServerCapability? {
            if(hostAddress==null) return null
            val capability = Capability.get(hostAddress, httpsOnly) ?: return null
            return ServerCapability(
                capability,
                CategoryList.getCategoryList(capability),
                MarkList.getMarkList(capability),
                RatingList.getRatingList(capability)
                )
        }
    }

}