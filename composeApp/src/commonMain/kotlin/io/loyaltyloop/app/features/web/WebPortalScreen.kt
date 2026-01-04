package io.loyaltyloop.app.features.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.config.AppConfig.WEB_URL
import io.loyaltyloop.app.platform.web.LoyaltyWebView
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.web_error_title
import loyaltyloop.composeapp.generated.resources.web_error_retry
import loyaltyloop.composeapp.generated.resources.web_loading_message
import org.jetbrains.compose.resources.stringResource

class WebPortalScreen(
    private val url: String? = null,
    private val headers: Map<String, String>
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var isLoading by remember { mutableStateOf(true) }
        var lastError by remember { mutableStateOf<String?>(null) }
        var reloadSignal by remember { mutableStateOf(0) }

        val finalUrl = remember {
            val baseUrl = url ?: WEB_URL
            val accessToken = headers["Authorization"]?.replace("Bearer ", "") ?: ""
            val refreshToken = headers["X-Refresh-Token"] ?: ""

            if (accessToken.isNotBlank() && refreshToken.isNotBlank() && baseUrl == WEB_URL) {
                // Проверяем, есть ли уже query params в URL
                val separator = if (baseUrl.contains("?")) "&" else "?"
                "$baseUrl${separator}accessToken=$accessToken&refreshToken=$refreshToken"
            } else {
                baseUrl
            }
        }


        LoyaltyScaffold(
            snackbarHostState = SnackbarHostState(),
            topBar = {
                TopAppBar(
                    title = { Text("LoyaltyLoops Web") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (lastError == null) {
                    LoyaltyWebView(
                        url = finalUrl,
                        headers = headers,
                        reloadSignal = reloadSignal,
                        onPageLoading = { isLoading = it },
                        onError = {
                            lastError = it
                            isLoading = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(Res.string.web_error_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = lastError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Button(onClick = {
                            lastError = null
                            isLoading = true
                            reloadSignal++
                        }) {
                            Text(stringResource(Res.string.web_error_retry))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(Res.string.web_loading_message),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}


