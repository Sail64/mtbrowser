package cn.ptdocs.librechatapp.di

import android.content.Context
import cn.ptdocs.librechatapp.data.LegacyMigrator
import cn.ptdocs.librechatapp.data.PrefCertReminderStore
import cn.ptdocs.librechatapp.data.PrefClientCertStore
import cn.ptdocs.librechatapp.data.PrefSiteRepository
import cn.ptdocs.librechatapp.domain.CertReminderScheduler
import cn.ptdocs.librechatapp.domain.CertificateAdvisor
import cn.ptdocs.librechatapp.domain.DefaultSslTrustPolicy
import cn.ptdocs.librechatapp.domain.NavigationPolicy
import cn.ptdocs.librechatapp.domain.SslTrustPolicy
import cn.ptdocs.librechatapp.domain.SiteRepository
import cn.ptdocs.librechatapp.domain.DefaultCertificateAdvisor
import cn.ptdocs.librechatapp.domain.DefaultNavigationPolicy

/**
 * 轻量依赖容器：应用的组合根。
 * 单 Activity 应用不值得引入 Hilt；接口边界已在 domain 层定义，
 * 将来替换实现只改这里。
 */
object AppGraph {

    lateinit var siteRepository: SiteRepository
        private set
    lateinit var certAdvisor: CertificateAdvisor
        private set
    lateinit var reminderScheduler: CertReminderScheduler
        private set
    lateinit var clientCertStore: PrefClientCertStore
        private set
    lateinit var sslTrustPolicy: SslTrustPolicy
        private set
    lateinit var navigationPolicy: NavigationPolicy
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        LegacyMigrator.migrateIfNeeded(appContext)
        siteRepository = PrefSiteRepository(appContext)
        clientCertStore = PrefClientCertStore(appContext)
        certAdvisor = DefaultCertificateAdvisor()
        reminderScheduler = PrefCertReminderStore(appContext)
        sslTrustPolicy = DefaultSslTrustPolicy(certAdvisor)
        navigationPolicy = DefaultNavigationPolicy()
    }
}
