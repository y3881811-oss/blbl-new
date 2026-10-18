package blbl.cat3399.util

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

object WbiSigner {
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    @Volatile private var cachedMixinKey: String? = null
    @Volatile private var cacheDate: String = ""

    fun sign(url: HttpUrl, client: OkHttpClient): HttpUrl {
        val mixinKey = getMixinKey(client) ?: return url
        val wts = System.currentTimeMillis() / 1000
        val params = url.queryParameterNames
            .associateWith { url.queryParameter(it) ?: "" }
            .toMutableMap()
        params["wts"] = wts.toString()
        
        val query = params.filterKeys { it != "w_rid" }
            .entries.sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${k}=${percentEncode(v)}" }
            
        val wRid = md5(query + mixinKey)

        return url.newBuilder()
            .setQueryParameter("wts", wts.toString())
            .setQueryParameter("w_rid", wRid)
            .build()
    }

    private fun getMixinKey(client: OkHttpClient): String? {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (cachedMixinKey != null && cacheDate == today) return cachedMixinKey

        return try {
            val request = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav").build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())
            val wbiImg = json.getJSONObject("data").getJSONObject("wbi_img")
            val imgKey = wbiImg.getString("img_url").substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.getString("sub_url").substringAfterLast("/").substringBefore(".")
            val rawKey = imgKey + subKey
            val mixin = MIXIN_KEY_ENC_TAB.joinToString("") { rawKey[it].toString() }.take(32)
            cachedMixinKey = mixin
            cacheDate = today
            mixin
        } catch (e: Exception) {
            cachedMixinKey
        }
    }

    private fun percentEncode(value: String): String {
        val filtered = value.replace(Regex("[!'()*]"), "")
        return java.net.URLEncoder.encode(filtered, "UTF-8")
            .replace("+", "%20").replace("%2A", "*").replace("%21", "!")
            .replace("%27", "'").replace("%28", "(").replace("%29", ")")
    }

    private fun md5(input: String): String {
        return MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}