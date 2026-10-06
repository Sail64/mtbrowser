package cn.ptdocs.librechatapp.domain.model

/** 站点（书签）：浏览器内可访问地址的持久化入口。 */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val renameFocusFix: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
