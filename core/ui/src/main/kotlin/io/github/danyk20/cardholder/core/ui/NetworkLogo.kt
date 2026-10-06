package io.github.danyk20.cardholder.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.R as DesignR
import io.github.danyk20.cardholder.core.model.CardNetwork

/** Bundled, public-domain logo of a card network; `null` for unknown networks. */
@get:DrawableRes
val CardNetwork.logo: Int?
    get() = when (this) {
        CardNetwork.VISA -> DesignR.drawable.network_visa
        CardNetwork.MASTERCARD -> DesignR.drawable.network_mastercard
        CardNetwork.AMERICAN_EXPRESS -> DesignR.drawable.network_american_express
        CardNetwork.DISCOVER -> DesignR.drawable.network_discover
        CardNetwork.DINERS_CLUB -> DesignR.drawable.network_diners_club
        CardNetwork.JCB -> DesignR.drawable.network_jcb
        CardNetwork.UNIONPAY -> DesignR.drawable.network_unionpay
        CardNetwork.MAESTRO -> DesignR.drawable.network_maestro
        CardNetwork.UNKNOWN -> null
    }

/** A card network logo on a white plate, readable on any card colour. */
@Composable
fun NetworkLogo(network: CardNetwork, modifier: Modifier = Modifier) {
    val logo = network.logo ?: return
    Box(
        modifier
            .height(32.dp)
            .background(Color.White, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Image(painterResource(logo), contentDescription = network.displayName)
    }
}
