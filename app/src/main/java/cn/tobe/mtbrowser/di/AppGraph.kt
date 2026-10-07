package cn.tobe.mtbrowser.di

import android.content.Context
import cn.tobe.mtbrowser.data.LegacyMigrator
import cn.tobe.mtbrowser.data.PrefCertReminderStore
import cn.tobe.mtbrowser.data.PrefClientCertStore
import cn.tobe.mtbrowser.data.PrefProbeThrottleStore
import cn.tobe.mtbrowser.data.PrefSiteRepository
import cn.tobe.mtbrowser.domain.CertReminderScheduler
import cn.tobe.mtbrowser.domain.CertificateAdvisor
import cn.tobe.mtbrowser.domain.DefaultSslTrustPolicy
import cn.tobe.mtbrowser.domain.NavigationPolicy
import cn.tobe.mtbrowser.domain.ProbeThrottle
import cn.tobe.mtbrowser.domain.SslTrustPolicy
import cn.tobe.mtbrowser.domain.SiteRepository
import cn.tobe.mtbrowser.domain.DefaultCertificateAdvisor
import cn.tobe.mtbrowser.domain.DefaultNavigationPolicy
import cn.tobe.mtbrowser.domain.DomainWhitelist

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
    lateinit var probeThrottle: ProbeThrottle
        private set
    lateinit var domainWhitelist: DomainWhitelist
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
        probeThrottle = PrefProbeThrottleStore(appContext)
        domainWhitelist = DomainWhitelist(appContext)
    }
}
