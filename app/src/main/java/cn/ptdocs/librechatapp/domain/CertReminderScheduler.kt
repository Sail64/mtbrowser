package cn.ptdocs.librechatapp.domain

import cn.ptdocs.librechatapp.domain.model.CertKind

/**
 * 证书提醒调度：决定「弹不弹」并记录已提醒状态。
 * 目标行为：同一 host + 证书类别每 24 小时最多提醒一次（持久化，重启不重置）。
 */
interface CertReminderScheduler {
    fun shouldRemind(host: String, kind: CertKind, now: Long = System.currentTimeMillis()): Boolean
    fun markReminded(host: String, kind: CertKind, now: Long = System.currentTimeMillis())
}
