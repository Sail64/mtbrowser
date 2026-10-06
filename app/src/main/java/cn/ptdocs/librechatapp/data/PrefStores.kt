package cn.ptdocs.librechatapp.data

import android.content.Context
import cn.ptdocs.librechatapp.domain.CertReminderScheduler
import cn.ptdocs.librechatapp.domain.ClientCertSelector
import cn.ptdocs.librechatapp.domain.SiteRepository
import cn.ptdocs.librechatapp.domain.model.CertKind
import cn.ptdocs.librechatapp.domain.model.Site
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

private const val FILE = "mtls_webview"

/** 按 host 隔离的客户端证书 alias 存储。 */
class PrefClientCertStore(context: Context) : ClientCertSelector {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val key = "client_cert_aliases"

    private fun load(): MutableMap<String, String> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).associate {
                val o = arr.getJSONObject(it)
                o.getString("host") to o.getString("alias")
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun save(map: Map<String, String>) {
        val arr = JSONArray()
        map.forEach { (h, a) -> arr.put(JSONObject().put("host", h).put("alias", a)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    override fun cachedAlias(host: String): String? = load()[host]

    override fun saveAlias(host: String, alias: String) {
        val map = load()
        map[host] = alias
        save(map)
    }

    override fun clearAlias(host: String) {
        val map = load()
        map.remove(host)
        save(map)
    }

    /** KeyChain 选择器实现在 platform 层；此处为存储职责的默认 no-op 抛出。 */
    override fun chooseAlias(
        host: String,
        port: Int,
        keyTypes: Array<String>,
        principals: Array<java.security.Principal?>,
        callback: (String?) -> Unit
    ) = throw UnsupportedOperationException("由 KeyChainCertSelector 实现")
}

/** 证书提醒时间戳存储：同一 host + 类别每 24h 最多提醒一次（持久化）。 */
class PrefCertReminderStore(context: Context) : CertReminderScheduler {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val key = "cert_warn_at"
    private val intervalMs = 24 * 60 * 60 * 1000L

    private fun load(): MutableMap<String, Long> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).associate {
                val o = arr.getJSONObject(it)
                o.getString("k") to o.getLong("at")
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun k(host: String, kind: CertKind) = "${kind.name}:$host"

    override fun shouldRemind(host: String, kind: CertKind, now: Long): Boolean {
        val last = load()[k(host, kind)] ?: return true
        return now - last >= intervalMs
    }

    override fun markReminded(host: String, kind: CertKind, now: Long) {
        val map = load()
        map[k(host, kind)] = now
        val arr = JSONArray()
        map.forEach { (key, at) -> arr.put(JSONObject().put("k", key).put("at", at)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

/** 站点（书签）仓库：SharedPreferences + JSON 实现。 */
class PrefSiteRepository(context: Context) : SiteRepository {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val key = "sites"

    private fun load(): MutableList<Site> {
        val raw = prefs.getString(key, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Site(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    renameFocusFix = o.optBoolean("renameFocusFix", false),
                    createdAt = o.optLong("createdAt", 0L)
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(sites: List<Site>) {
        val arr = JSONArray()
        sites.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("renameFocusFix", it.renameFocusFix)
                    .put("createdAt", it.createdAt)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    override fun all(): List<Site> = load()

    override fun byHost(host: String): Site? =
        load().firstOrNull { hostOf(it.url) == host }

    override fun findByUrl(url: String): Site? =
        load().firstOrNull { normalize(it.url) == normalize(url) }

    override fun add(name: String, url: String, renameFocusFix: Boolean): Site {
        val sites = load()
        val site = Site(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { hostOf(url) ?: url },
            url = url,
            renameFocusFix = renameFocusFix
        )
        sites.add(site)
        save(sites)
        return site
    }

    override fun update(site: Site) {
        val sites = load()
        val idx = sites.indexOfFirst { it.id == site.id }
        if (idx >= 0) {
            sites[idx] = site
            save(sites)
        }
    }

    override fun remove(id: String) {
        val sites = load()
        sites.removeAll { it.id == id }
        save(sites)
    }

    companion object {
        fun hostOf(url: String): String? = try {
            URI(url).host
        } catch (e: Exception) {
            null
        }

        fun normalize(url: String): String = url.trim().trimEnd('/')
    }
}
