package cn.tobe.mtbrowser.domain

import cn.tobe.mtbrowser.domain.model.Site

/** 站点（书签）仓库：主页、收藏、按 host 查询的唯一数据源。 */
interface SiteRepository {
    fun all(): List<Site>
    fun byHost(host: String): Site?
    fun add(name: String, url: String): Site
    fun update(site: Site)
    fun remove(id: String)
    fun findByUrl(url: String): Site?
}
