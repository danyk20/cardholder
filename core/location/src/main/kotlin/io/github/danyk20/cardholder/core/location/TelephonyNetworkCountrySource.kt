package io.github.danyk20.cardholder.core.location

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.country.NetworkCountrySource
import io.github.danyk20.cardholder.core.model.CountryCode
import javax.inject.Inject

/**
 * The country the mobile network is registered in. The modem already holds this value, so reading it
 * needs no permission, no GPS and almost no battery. Wi‑Fi alone can't give a country, and this doesn't need it.
 */
internal class TelephonyNetworkCountrySource @Inject constructor(@ApplicationContext private val context: Context) :
    NetworkCountrySource {
    override fun networkCountry(): CountryCode? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return null
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        return telephony.networkCountry()
    }

    private fun TelephonyManager.networkCountry(): CountryCode? {
        parse(networkCountryIso)?.let { return it }
        // With two SIMs the default one may be out of network: take the first modem that is registered.
        return (0 until activeModemCount).firstNotNullOfOrNull { parse(getNetworkCountryIso(it)) }
    }

    /** Empty or non-ISO values, which some devices report without a network, are no country. */
    private fun parse(raw: String?): CountryCode? = raw?.let(CountryCode::of)
}
