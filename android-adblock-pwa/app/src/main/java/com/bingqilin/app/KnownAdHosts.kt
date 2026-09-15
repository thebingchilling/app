package com.bingqilin.app

/**
 * Domains commonly used by the popup/popunder ad networks that free video
 * streaming sites embed in their players (propellerads, exoclick, etc).
 * Navigations to these are dropped outright rather than handed to the
 * system browser, even if they carry a "user gesture" (a fake click-catcher
 * overlay on the play button counts as one).
 *
 * This list is intentionally small and specific to what shows up on player
 * pages - it isn't a general-purpose ad/tracker blocklist.
 */
private val KNOWN_AD_HOST_SUFFIXES = setOf(
    "propellerads.com",
    "propellerclick.com",
    "adsterra.com",
    "exoclick.com",
    "juicyads.com",
    "revenuehits.com",
    "popads.net",
    "popcash.net",
    "adcash.com",
    "hilltopads.net",
    "clickadu.com",
    "trafficjunky.com",
    "trafficjunky.net",
    "adnium.com",
    "onclickmax.com",
    "adskeeper.com",
    "mgid.com",
    "smartyads.com",
    "yllix.com",
)

fun isKnownAdHost(host: String): Boolean =
    KNOWN_AD_HOST_SUFFIXES.any { suffix ->
        host.equals(suffix, ignoreCase = true) || host.endsWith(".$suffix", ignoreCase = true)
    }
