package io.loyaltyloop.app.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import com.yandex.mapkit.map.CameraPosition as YMKCameraPosition
import com.yandex.mapkit.map.Map as YMKMap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import com.yandex.mapkit.map.PlacemarkMapObject
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.empty
import loyaltyloop.composeapp.generated.resources.map_type_flowers
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

import com.yandex.mapkit.map.CircleMapObject
import com.yandex.mapkit.geometry.Circle
import io.loyaltyloop.shared.models.GeoLocation

@Composable
actual fun YandexMap(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    userLocation: GeoLocation?,
    searchAreaCenter: GeoLocation?,
    searchRadius: Int?,
    onMapClick: () -> Unit,
    onMarkerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Инициализируем MapView
    val mapView = remember {
        MapView(context).apply {
            // Важно: onStart нужно вызвать сразу, если MapView создается внутри remember,
            // иначе карта может быть черной
            // Но лучше управлять этим через DisposableEffect ниже
        }
    }

    // Храним коллекцию объектов карты
    // Используем remember, чтобы не создавать новую коллекцию при рекомпозиции
    val mapObjects = remember { mapView.map.mapObjects.addCollection() }
    val currentPlacemarks = remember { mutableMapOf<String, PlacemarkMapObject>() }

    // Слушатель тапов по маркерам
    val tapListener = remember {
        MapObjectTapListener { mapObject, _ ->
            val id = mapObject.userData as? String
            if (id != null) {
                onMarkerClick(id)
                true // событие обработано
            } else {
                false
            }
        }
    }

    // Слушатель тапов по карте (пустое место)
    val inputListener = remember {
        object : InputListener {
            override fun onMapTap(map: YMKMap, point: Point) {
                onMapClick()
            }

            override fun onMapLongTap(map: YMKMap, point: Point) {
                // Ничего не делаем
            }
        }
    }

    // Привязываем слушатель к карте один раз
    DisposableEffect(mapView) {
        mapView.map.addInputListener(inputListener)
        onDispose {
            mapView.map.removeInputListener(inputListener)
        }
    }

    // Управление камерой (плавное перемещение)
    LaunchedEffect(cameraPosition) {
        mapView.map.move(
            YMKCameraPosition(
                Point(cameraPosition.lat, cameraPosition.lon), // Исправлен конструктор Point
                cameraPosition.zoom,
                0.0f,
                0.0f
            ),
            Animation(Animation.Type.SMOOTH, 0.5f),
            null
        )
    }

    // Отрисовка радиуса поиска
    val searchCircle = remember { mutableStateOf<CircleMapObject?>(null) }
    val strokeColor = remember { "#2563EB".toColorInt() }
    val fillColor = remember { "#1A2563EB".toColorInt() }

    LaunchedEffect(searchAreaCenter, searchRadius) {
        if (searchAreaCenter != null && searchRadius != null) {
            val circleGeom = Circle(
                Point(searchAreaCenter.lat, searchAreaCenter.lon),
                searchRadius.toFloat()
            )

            if (searchCircle.value == null) {
                val c = mapObjects.addCircle(circleGeom)
                c.strokeColor = strokeColor
                c.strokeWidth = 1.5f * density.density
                c.fillColor = fillColor
                searchCircle.value = c
            } else {
                searchCircle.value?.geometry = circleGeom
            }
        } else {
            searchCircle.value?.let { mapObjects.remove(it) }
            searchCircle.value = null
        }
    }

    // Отрисовка локации пользователя
    val userLocationPlacemark = remember { mutableStateOf<PlacemarkMapObject?>(null) }
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            val point = Point(userLocation.lat, userLocation.lon)
            val bitmap = generateUserLocationBitmap(density)
            val imageProvider = ImageProvider.fromBitmap(bitmap)

            if (userLocationPlacemark.value == null) {
                val p = mapObjects.addPlacemark(point, imageProvider)
                p.zIndex = 100f
                userLocationPlacemark.value = p
            } else {
                userLocationPlacemark.value?.geometry = point
                userLocationPlacemark.value?.setIcon(imageProvider)
            }
        } else {
            userLocationPlacemark.value?.let { mapObjects.remove(it) }
            userLocationPlacemark.value = null
        }
    }

    // Отрисовка маркеров
    LaunchedEffect(markers){
        scope.launch {
            // 1. Удаляем маркеры, которых больше нет в новом списке
            val newIds = markers.map { it.id }.toSet()
            val iterator = currentPlacemarks.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in newIds) {
                    mapObjects.remove(entry.value) // Удаляем с карты
                    iterator.remove() // Удаляем из нашей мапы
                }
            }

            // 2. Добавляем новые или обновляем существующие
            markers.forEach { marker ->
                val existingPlacemark = currentPlacemarks[marker.id]

                // Генерируем картинку (suspend функция из прошлого ответа)
                val bitmap = generatePinBitmap(density, marker)
                val imageProvider = ImageProvider.fromBitmap(bitmap)

                if (existingPlacemark != null) {
                    // ОБНОВЛЕНИЕ: Если маркер уже есть, просто меняем иконку и позицию (без мигания)
                    // Важно: проверяем, изменилась ли иконка/позиция, чтобы лишний раз не дёргать натив
                    existingPlacemark.geometry = Point(marker.lat, marker.lon)
                    existingPlacemark.setIcon(imageProvider)
                } else {
                    // СОЗДАНИЕ: Если маркера нет, создаем новый
                    val placemark = mapObjects.addPlacemark(
                        Point(marker.lat, marker.lon),
                        imageProvider
                    )
                    placemark.userData = marker.id
                    placemark.addTapListener(tapListener)
                    currentPlacemarks[marker.id] = placemark
                }
            }
        }
    }

    // Управление жизненным циклом MapKit
    DisposableEffect(Unit) {
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        onDispose {
            mapView.onStop()
            MapKitFactory.getInstance().onStop()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

// --- ГЕНЕРАТОР БИТМАПОВ (Остался без изменений, он правильный) ---
private suspend fun generatePinBitmap(density: Density, marker: MapMarker): Bitmap {
    val isActive = marker.isSelected

    val floatDensity = density.density

    val height = (if (isActive) 40 else 36) * floatDensity
    val padding = 12 * floatDensity
    val iconSize = 20 * floatDensity

    // TODO вынестив ресурсы
    val bgColor = if (isActive) "#111827".toColorInt() else Color.WHITE
    val textColor = if (isActive) Color.WHITE else "#1F2937".toColorInt()
    val strokeColor = if (isActive) "#111827".toColorInt() else "#2563EB".toColorInt()

    val emoji = getEmojiForType(marker.type)
    val labelRes = if (isActive) getLabelResource(marker.type) else  Res.string.empty
    val text = getString(labelRes)

    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * floatDensity
        color = textColor
        isFakeBoldText = true
    }

    val textWidth = if (isActive) paintText.measureText(text) else 0f
    val totalWidth = if (isActive) {
        (padding * 2) + iconSize + (8 * floatDensity) + textWidth
    } else {
        height
    }

    val bitmap = createBitmap(totalWidth.toInt(), height.toInt())
    val canvas = Canvas(bitmap)

    val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
        setShadowLayer(4 * floatDensity, 0f, 2 * floatDensity, "#40000000".toColorInt())
    }
    val rect = RectF(0f, 0f, totalWidth, height)
    canvas.drawRoundRect(rect, height / 2, height / 2, paintBg)

    if (!isActive) {
        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * floatDensity
        }
        rect.inset(1f * floatDensity, 1f * floatDensity)
        canvas.drawRoundRect(rect, height / 2, height / 2, paintStroke)
    }

    val paintEmoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if(isActive) 18 else 20) * floatDensity
        textAlign = Paint.Align.CENTER
    }

    val iconX = if (isActive) padding + (iconSize / 2) else totalWidth / 2
    // Центрирование текста по вертикали (baseline magic)
    val iconY = (height / 2) - ((paintEmoji.descent() + paintEmoji.ascent()) / 2)

    canvas.drawText(emoji, iconX, iconY, paintEmoji)

    if (isActive) {
        val textX = padding + iconSize + (8 * floatDensity)
        val textY = (height / 2) - ((paintText.descent() + paintText.ascent()) / 2)
        canvas.drawText(text, textX, textY, paintText)
    }

    return bitmap
}

private fun generateUserLocationBitmap(density: Density): Bitmap {
    val floatDensity = density.density
    val size = 24 * floatDensity
    val bitmap = createBitmap(size.toInt(), size.toInt())
    val canvas = Canvas(bitmap)

    val centerX = size / 2
    val centerY = size / 2
    val radius = size / 2

    // Белая обводка
    val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4 * floatDensity, 0f, 2 * floatDensity, "#40000000".toColorInt())
    }
    canvas.drawCircle(centerX, centerY, radius - (2 * floatDensity), paintWhite)

    // Синяя точка
    val paintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2563EB".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, radius - (5 * floatDensity), paintBlue)

    return bitmap
}