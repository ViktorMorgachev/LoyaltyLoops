package io.loyaltyloop.app.features.forceupdate

import androidx.compose.foundation.Image
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
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(Res.string.force_update_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.force_update_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

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

