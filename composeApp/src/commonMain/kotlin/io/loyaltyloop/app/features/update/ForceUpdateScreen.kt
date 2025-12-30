package io.loyaltyloop.app.features.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import io.loyaltyloop.app.platform.UrlOpener
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.force_update_btn
import loyaltyloop.composeapp.generated.resources.force_update_message
import loyaltyloop.composeapp.generated.resources.force_update_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class ForceUpdateScreen(
    private val storeUrl: String
) : Screen {

    @Composable
    override fun Content() {
        val urlOpener = koinInject<UrlOpener>()

        Box(
            modifier = Modifier.Companion
                .background(color = MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Companion.Center
        ) {
            Column(
                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.Companion.size(96.dp),
                    colorFilter = ColorFilter.Companion.tint(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.Companion.height(32.dp))

                Text(
                    text = stringResource(Res.string.force_update_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Companion.Center
                )

                Spacer(modifier = Modifier.Companion.height(16.dp))

                Text(
                    text = stringResource(Res.string.force_update_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Companion.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.Companion.height(48.dp))

                Button(
                    onClick = {
                        urlOpener.openUrl(storeUrl)
                    }
                ) {
                    Text(stringResource(Res.string.force_update_btn))
                }
            }
        }
    }
}