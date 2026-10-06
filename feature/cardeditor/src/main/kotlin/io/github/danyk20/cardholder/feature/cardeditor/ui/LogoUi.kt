package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.model.ShopLogo
import io.github.danyk20.cardholder.core.ui.LogoBadge
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.cardeditor.LogoImage
import io.github.danyk20.cardholder.feature.cardeditor.R

internal class LogoActions(
    val onUseOfficial: () -> Unit,
    val onUpload: () -> Unit,
    val onRemove: () -> Unit,
    val onDismissChoice: () -> Unit,
)

/** Asked when a shop with an official logo is selected: use it, upload one or keep the card blank. */
@Composable
internal fun LogoChoiceDialog(shop: Shop, actions: LogoActions) {
    val logo = shop.logo ?: return
    AlertDialog(
        onDismissRequest = actions.onDismissChoice,
        properties = secureDialogProperties(),
        title = { Text(stringResource(R.string.editor_logo_choice_title, shop.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.editor_logo_choice_message, shop.name))
                Text(
                    text = licenseText(logo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = actions.onUseOfficial) { Text(stringResource(R.string.editor_logo_use_official)) }
                TextButton(onClick = actions.onUpload) { Text(stringResource(R.string.editor_logo_upload_own)) }
                TextButton(onClick = actions.onRemove) { Text(stringResource(R.string.editor_logo_keep_blank)) }
            }
        },
    )
}

/** Current logo with buttons to use the official one (if the shop has one), upload one or remove it. */
@Composable
internal fun LogoRow(logo: LogoImage, shop: Shop?, isDownloading: Boolean, actions: LogoActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_logo), style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            when {
                isDownloading -> CircularProgressIndicator(Modifier.size(32.dp))

                logo is LogoImage.Existing -> LogoBadge(model = logo.ref, contentDescription = null)

                logo is LogoImage.Downloaded -> LogoBadge(model = logo.bytes, contentDescription = null)

                logo is LogoImage.Picked -> LogoBadge(model = logo.uri, contentDescription = null)

                else -> Text(
                    stringResource(R.string.editor_logo_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shop?.logo != null) {
                OutlinedButton(onClick = actions.onUseOfficial, enabled = !isDownloading) {
                    Text(stringResource(R.string.editor_logo_official))
                }
            }
            OutlinedButton(onClick = actions.onUpload, enabled = !isDownloading) {
                Text(stringResource(R.string.editor_logo_upload))
            }
            if (logo != LogoImage.None) {
                TextButton(onClick = actions.onRemove) { Text(stringResource(R.string.editor_logo_remove)) }
            }
        }
    }
}

@Composable
private fun licenseText(logo: ShopLogo): String = logo.attribution
    ?.let { stringResource(R.string.editor_logo_attribution, logo.license, it) }
    ?: stringResource(R.string.editor_logo_license, logo.license)
