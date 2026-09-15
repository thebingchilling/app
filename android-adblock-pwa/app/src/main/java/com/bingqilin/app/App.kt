package com.bingqilin.app

import android.app.Application
import io.github.edsuns.adfilter.AdFilter

/**
 * AdGuard's own subscription URLs, in the EasyList-compatible format the
 * ad-filter engine expects. These are added once on first launch so the
 * app blocks ads/trackers without asking the user to pick lists manually.
 */
private val DEFAULT_FILTER_SUBSCRIPTIONS = listOf(
    "AdGuard Base filter" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
    "AdGuard Mobile Ads filter" to "https://filters.adtidy.org/extension/chromium/filters/11.txt",
    "AdGuard Tracking Protection filter" to "https://filters.adtidy.org/extension/chromium/filters/3.txt",
)

class App : Application() {

    lateinit var adFilter: AdFilter
        private set

    override fun onCreate() {
        super.onCreate()
        adFilter = AdFilter.create(this)

        if (!adFilter.hasInstallation) {
            val viewModel = adFilter.viewModel
            viewModel.isEnabled.value = true
            DEFAULT_FILTER_SUBSCRIPTIONS.forEach { (name, url) ->
                val filter = viewModel.addFilter(name, url)
                viewModel.setFilterEnabled(filter.id, true)
                viewModel.download(filter.id)
            }
        }
    }
}
