package io.loyaltyloop.app.features.join

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class JoinCompanyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<JoinCompanyScreenModel>()
        val state by viewModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        val keyboardController = LocalSoftwareKeyboardController.current

        // Единая обработка событий
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is JoinCompanyScreenModel.Event.NavigateBack -> {
                        keyboardController?.hide()
                        navigator.pop()
                    }
                    is JoinCompanyScreenModel.Event.ShowMessage -> {
                        launch {
                            snackbarHostState.show(event.message, event.type)
                        }
                    }
                }
            }
        }

        JoinCompanyContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCompanyContent(
    state: JoinCompanyScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onAction: (JoinCompanyScreenModel.Action) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LoyaltyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.join_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(JoinCompanyScreenModel.Action.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.join_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.code,
                onValueChange = { onAction(JoinCompanyScreenModel.Action.OnCodeChanged(it)) },
                label = { Text(stringResource(Res.string.join_code_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading,

                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onAction(JoinCompanyScreenModel.Action.OnSubmitClicked)
                    }
                ),

                // Красивая ошибка
                isError = state.error != null,
                supportingText = {
                    state.error?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error)
                    }
                },
                trailingIcon = {
                    if (state.error != null) {
                        Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                    }
                },
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            LoyaltyButton(
                text = stringResource(Res.string.join_btn_submit),
                onClick = { onAction(JoinCompanyScreenModel.Action.OnSubmitClicked) },
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.code.isNotBlank()
            )
        }
    }
}