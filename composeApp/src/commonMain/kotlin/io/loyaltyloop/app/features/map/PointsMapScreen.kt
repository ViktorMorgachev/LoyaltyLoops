package io.loyaltyloop.app.features.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.internal.BackHandler
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.loyaltyloop.app.ui.components.map.YandexMap
import io.loyaltyloop.app.ui.components.map.getEmojiForType
import io.loyaltyloop.app.ui.components.map.getLabelResource
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import io.loyaltyloop.app.ui.components.map.formatDistance
import org.jetbrains.compose.resources.stringResource
import loyaltyloop.composeapp.generated.resources.*

class PointsMapScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, InternalVoyagerApi::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PointsMapScreenModel>()
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        // --- PERMISSIONS ---
        val factory = rememberPermissionsControllerFactory()
        val controller = remember(factory) { factory.createPermissionsController() }
        BindEffect(controller)

        // 1. ЛОГИКА ВИДИМОСТИ ПАНЕЛИ
        // Панель видна, если: выбран маркер ИЛИ (введен текст поиска И есть результаты)
        val isSheetVisible = remember(state.selectedPointId, state.query, state.visiblePoints) {
            state.selectedPointId != null || (state.query.isNotEmpty() && state.visiblePoints.isNotEmpty())
        }

        val displayedPoints = remember(state.selectedPointId, state.visiblePoints) {
            if (state.selectedPointId != null) {
                state.visiblePoints.filter { it.id == state.selectedPointId }
            } else {
                state.visiblePoints
            }
        }

        // Высота "выглядывания" панели (Peek Height)
        // 0.dp скрывает панель полностью (убирает белую полосу).
        // 140.dp показывает одну карточку (достаточно для превью).
        val targetPeekHeight = remember(state.selectedPointId, state.query, state.visiblePoints) {
            when {
                // Если выбрана конкретная точка -> высота одной карточки (~130dp)
                state.selectedPointId != null -> 130.dp

                // Если просто поиск и есть результаты -> высота двух карточек (~230dp)
                state.query.isNotEmpty() && state.visiblePoints.isNotEmpty() -> 230.dp

                // Иначе скрываем
                else -> 0.dp
            }
        }

        val keyboardController =
            androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(state.selectedPointId) {
            if (state.selectedPointId != null) {
                keyboardController?.hide()
            }
        }


        // Анимируем высоту (плавное появление/исчезновение)
        val animatedPeekHeight by animateDpAsState(
            targetValue = targetPeekHeight,
            animationSpec = tween(durationMillis = 400),
            label = "peekHeight"
        )

        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                skipHiddenState = false
            )
        )

        // Handle Back Button
        val isExpanded =
            scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded || scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded
        BackHandler(enabled = isExpanded) {
            scope.launch { scaffoldState.bottomSheetState.hide() }
        }

        var showRationaleDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showRadiusMenu by remember { mutableStateOf(false) }

        val requestLocationPermission = remember {
            suspend {
                try {
                    controller.providePermission(Permission.LOCATION)
                    viewModel.onLocateMe()
                } catch (e: DeniedAlwaysException) {
                    showSettingsDialog = true
                } catch (e: DeniedException) {
                    showRationaleDialog = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        var showTypeMenu by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        LaunchedEffect(state.selectedPointId) {
            state.selectedPointId?.let { id ->
                val index = state.visiblePoints.indexOfFirst { it.id == id }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
            }
        }

        // Если панель должна быть скрыта, принудительно сворачиваем (на случай, если она была Expanded)
        LaunchedEffect(isSheetVisible) {
            if (!isSheetVisible) {
                scaffoldState.bottomSheetState.partialExpand()
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

            val isImeVisible = WindowInsets.ime.getBottom(density) > 0
            val screenHeightPx = with(density) { maxHeight.toPx() }

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                // 2. ДИНАМИЧЕСКИЙ PEEK HEIGHT (исправляет белое пространство)
                sheetPeekHeight = animatedPeekHeight,
                sheetShadowElevation = 8.dp,
                // sheetShadowElevation = if (isSheetVisible) 8.dp else 0.dp,
                sheetContent = {
                    val navBarPadding =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp + navBarPadding)
                    ) {
                        items(displayedPoints, key = { it.id }) { point ->
                            PointListItem(
                                point = point,
                                isSelected = point.id == state.selectedPointId,
                                onClick = {
                                    viewModel.onMarkerClicked(point.id)
                                    scope.launch { scaffoldState.bottomSheetState.expand() }
                                }
                            )
                            if (displayedPoints.size > 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                },
                containerColor = Color.Transparent
            ) { padding ->

                val fabTranslationY by remember {
                    derivedStateOf {
                        val sheetState = scaffoldState.bottomSheetState
                        try {
                            val offset = sheetState.requireOffset()
                            // Здесь используем screenHeightPx, который мы получили выше
                            val sheetHeightVisible = screenHeightPx - offset

                            if (sheetHeightVisible > 0) {
                                -sheetHeightVisible - (16 * density.density)
                            } else {
                                -(16 * density.density)
                            }
                        } catch (e: Exception) {
                            0f
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    YandexMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPosition = state.cameraPosition,
                        markers = state.markers,
                        onMapClick = {
                            // 3. ПЛАВНОЕ ЗАКРЫТИЕ ПО КЛИКУ НА КАРТУ
                            viewModel.onMapClicked() // Сброс выделения
                            viewModel.onSearchQueryChanged("") // Очистка поиска -> isSheetVisible станет false -> сработает анимация
                            scope.launch {
                                scaffoldState.bottomSheetState.partialExpand()
                            }
                        },
                        onMarkerClick = { id ->
                            viewModel.onMarkerClicked(id)
                            // При клике на маркер чуть приоткрываем или разворачиваем, по желанию
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        }
                    )

                    // ВЕРХНЯЯ ПАНЕЛЬ
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (state.query.isEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.map_search_hint),
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = state.query,
                                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                if (state.query.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.onSearchQueryChanged("")
                                                viewModel.onMapClicked() // также сбрасываем выделение
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = state.typeFilter != null,
                                    onClick = { showTypeMenu = true },
                                    label = {
                                        Text(state.typeFilter?.let {
                                            stringResource(
                                                getLabelResource(
                                                    it
                                                )
                                            )
                                        }
                                            ?: stringResource(Res.string.map_filter_all_types))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.FilterList,
                                            null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = true, // Всегда активен, так как радиус есть всегда
                                    onClick = { showRadiusMenu = true },
                                    label = { Text("Радиус: ${formatDistance(state.radiusMeters)}") },
                                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = state.openNow,
                                    onClick = { viewModel.onOpenNowChanged(!state.openNow) },
                                    label = { Text(stringResource(Res.string.map_filter_open)) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                                )
                            }
                        }
                    }

                    // 4. КНОПКА ГЕОЛОКАЦИИ С "УМНЫМ" ОТСТУПОМ
                    // Вычисляем отступ: высота панели + фиксированный отступ.
                    val fabPaddingBottom by animateDpAsState(
                        targetValue = animatedPeekHeight + 16.dp,
                        label = "fabPadding"
                    )

                    AnimatedVisibility(
                        visible = !isImeVisible, // Скрываем, если клавиатура открыта
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .graphicsLayer { translationY = fabTranslationY }
                    ) {
                        FloatingActionButton(
                            onClick = { scope.launch { controller.providePermission(Permission.LOCATION); viewModel.onLocateMe() } },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
                            }
                        }
                    }
                }
            }

            if (showRationaleDialog) {
                AlertDialog(
                    onDismissRequest = { showRationaleDialog = false },
                    title = { Text(stringResource(Res.string.permission_location_rationale_title)) },
                    text = { Text(stringResource(Res.string.permission_location_rationale_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showRationaleDialog =
                                false; scope.launch { requestLocationPermission() }
                        }) { Text(stringResource(Res.string.permission_allow)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRationaleDialog = false
                        }) { Text(stringResource(Res.string.common_cancel)) }
                    }
                )
            }
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text(stringResource(Res.string.permission_location_denied_title)) },
                    text = { Text(stringResource(Res.string.permission_location_denied_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showSettingsDialog = false; controller.openAppSettings()
                        }) { Text(stringResource(Res.string.permission_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showSettingsDialog = false
                        }) { Text(stringResource(Res.string.common_cancel)) }
                    }
                )
            }

            if (showTypeMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showTypeMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
                    val bottomPadding = navBarInsets.calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
                    ) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(Res.string.map_filter_all_types)) },
                                modifier = Modifier.clickable {
                                    viewModel.onTypeFilterChanged(null)
                                    showTypeMenu = false
                                }
                            )
                            HorizontalDivider()
                        }

                        items(TradingPointType.entries) { type ->
                            ListItem(
                                headlineContent = { Text(stringResource(getLabelResource(type))) },
                                modifier = Modifier.clickable {
                                    viewModel.onTypeFilterChanged(type)
                                    showTypeMenu = false
                                }
                            )
                            if (type.ordinal < TradingPointType.entries.size -1){
                                HorizontalDivider()
                            }

                        }
                    }
                }
            }

            if (showRadiusMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showRadiusMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            // Учитываем отступ снизу для навигации
                            .padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    ) {
                        Text(
                            text = "Радиус поиска",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = formatDistance(state.radiusMeters),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Slider(
                            value = state.radiusMeters.toFloat(),
                            onValueChange = { viewModel.onRadiusChanged(it.toInt()) },
                            valueRange = state.minRadiusMeters.toFloat()..state.maxRadiusMeters.toFloat(), // От 500м до 15км
                            steps = ((state.maxRadiusMeters-state.minRadiusMeters)/500 - 1),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDistance(state.minRadiusMeters), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(formatDistance(state.maxRadiusMeters), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showRadiusMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(Res.string.common_apply)) // Или "Готово"
                        }
                    }
                }
                }
        }

    }

    @Composable
    fun PointListItem(
        point: TradingPointDto,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        // Подсветка, если выбрано
        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        val bgColor =
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.3f
            )

        // Логотип или заглушка (эмодзи)
        val logoUrl: String? = null // TODO: Заменить на point.logoUrl когда появится в DTO
        val emoji = getEmojiForType(point.type)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватарка
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                if (logoUrl != null) {
                    KamelImage(
                        resource = asyncPainterResource(logoUrl),
                        contentDescription = point.name,
                        contentScale = ContentScale.Crop,
                        onLoading = {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 28.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Расстояние справа сверху
                    point.distanceMeters?.let { meters ->
                        val dist = formatDistance(meters.toInt())
                        Text(
                            text = dist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = point.address ?: stringResource(Res.string.point_no_address),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Статус и тип
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isOpen = point.isOpenNow == true

                    // Индикатор статуса
                    Surface(
                        color = if (isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isOpen) stringResource(Res.string.common_open) else stringResource(
                                Res.string.common_closed
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOpen) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Название типа (Кофейня, Магазин...)
                    Text(
                        text = stringResource(getLabelResource(point.type)), // Лучше маппить на локализованный ресурс
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}