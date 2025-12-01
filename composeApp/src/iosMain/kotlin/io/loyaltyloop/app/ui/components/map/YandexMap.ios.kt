package io.loyaltyloop.app.ui.components.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.YandexMapsMobile.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.UIKit.*
import platform.CoreGraphics.CGPointMake
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun YandexMap(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMapClick: () -> Unit,
    onMarkerClick: (String) -> Unit
) {
    // Создаем карту (используем remember, чтобы не пересоздавать)
    val mapView = remember { YMKMapView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) }

    // Коллекция объектов на карте
    val mapObjects = remember { mapView.mapWindow.map.mapObjects.addCollection() }

    // Слушатель кликов по маркерам
    val tapListener = remember {
        MapObjectTapListenerImpl(onMarkerClick)
    }

    // Слушатель кликов по карте
    val inputListener = remember {
        InputListenerImpl(onMapClick)
    }

    // Привязка слушателя ввода (клики в пустоту)
    DisposableEffect(mapView) {
        mapView.mapWindow.map.addInputListener(inputListener)
        onDispose {
            mapView.mapWindow.map.removeInputListener(inputListener)
        }
    }

    // Управление камерой
    LaunchedEffect(cameraPosition) {
        val targetPoint = YMKPoint.pointWithLatitude(cameraPosition.lat, longitude = cameraPosition.lon)

        mapView.mapWindow.map.moveWithCameraPosition(
            YMKCameraPosition.cameraPositionWithTarget(
                targetPoint,
                zoom = cameraPosition.zoom,
                azimuth = 0.0f,
                tilt = 0.0f
            ),
            animationType = YMKAnimation(YMKAnimationType.YMKAnimationTypeSmooth, 0.5f),
            cameraCallback = null
        )
    }

    // Отрисовка маркеров
    LaunchedEffect(markers) {
        mapObjects.clear() // Очистка

        markers.forEach { marker ->
            val image = generatePinImage(marker)
            val imageProvider = YMKImageProvider.fromImage(image)

            val point = YMKPoint.pointWithLatitude(marker.lat, longitude = marker.lon)
            val placemark = mapObjects.addPlacemarkWithPoint(point, image = imageProvider)

            // В iOS userData это Any?, передаем ID
            placemark.userData = marker.id
            placemark.addTapListenerWithTapListener(tapListener)
        }
    }

    // Lifecycle (в iOS YMKMapKit тоже нужно стартовать)
    DisposableEffect(Unit) {
        // YMKMapKit.sharedInstance().onStart() // Обычно это делается в AppDelegate, но можно и тут
        // ВНИМАНИЕ: Если крашится на onStart, убери это и добавь в iOS App Delegate.
        // Но для MapView onStart нужен.
        mapView.onStart()
        onDispose {
            mapView.onStop()
            // YMKMapKit.sharedInstance().onStop()
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier
    )
}

// --- ЛИСТЕНЕРЫ (Bridge classes) ---

@OptIn(ExperimentalForeignApi::class)
private class MapObjectTapListenerImpl(
    val onMarkerClick: (String) -> Unit
) : NSObject(), YMKMapObjectTapListenerProtocol {
    override fun onMapObjectTapWithMapObject(mapObject: YMKMapObject, point: YMKPoint): Boolean {
        val id = mapObject.userData as? String
        if (id != null) {
            onMarkerClick(id)
            return true
        }
        return false
    }
}

@OptIn(ExperimentalForeignApi::class)
private class InputListenerImpl(
    val onMapClick: () -> Unit
) : NSObject(), YMKMapInputListenerProtocol {
    override fun onMapTapWithMap(map: YMKMap, point: YMKPoint) {
        onMapClick()
    }

    override fun onMapLongTapWithMap(map: YMKMap, point: YMKPoint) {
        // Ignored
    }
}

// --- ГЕНЕРАЦИЯ ИКОНОК (CORE GRAPHICS) ---

@OptIn(ExperimentalForeignApi::class)
private fun generatePinImage(marker: MapMarker): UIImage {
    val isActive = marker.isSelected

    // Цвета (UIColor)
    // #111827
    val bgActive = UIColor(red = 0.067, green = 0.094, blue = 0.153, alpha = 1.0)
    // #FFFFFF
    val bgInactive = UIColor.whiteColor
    // #1F2937
    val textInactive = UIColor(red = 0.122, green = 0.161, blue = 0.216, alpha = 1.0)
    // #2563EB
    val strokeBlue = UIColor(red = 0.145, green = 0.388, blue = 0.922, alpha = 1.0)

    val bgColor = if (isActive) bgActive else bgInactive
    val textColor = if (isActive) UIColor.whiteColor else textInactive
    val strokeColor = if (isActive) bgActive else strokeBlue

    // Тексты
    val emoji = getEmojiForType(marker.type)
    val label = if (isActive) getLabelForType(marker.type) else ""

    // Размеры
    val height = if (isActive) 40.0 else 36.0
    val padding = 12.0
    val iconSize = 20.0 // Для текста-эмодзи

    // Шрифты
    val labelFont = UIFont.boldSystemFontOfSize(13.0)
    val emojiFont = UIFont.systemFontOfSize(if (isActive) 18.0 else 20.0)

    // Расчет ширины текста
    val labelAttributes = mapOf(
        NSFontAttributeName to labelFont,
        NSForegroundColorAttributeName to textColor
    )
    val nsLabel = NSString.create(string = label)
    val labelSize = nsLabel.sizeWithAttributes(labelAttributes)
    val textWidth = if (isActive) labelSize.useContents { width } else 0.0

    val totalWidth = if (isActive) {
        (padding * 2) + iconSize + 8.0 + textWidth
    } else {
        height
    }

    val size = CGSizeMake(totalWidth, height)

    // --- РИСОВАНИЕ ---
    // scale = 0.0 означает использование масштаба экрана устройства (Retina)
    UIGraphicsBeginImageContextWithOptions(size, false, 0.0)

    val context = UIGraphicsGetCurrentContext()

    // 1. Фон (Rounded Rect)
    val rect = CGRectMake(0.0, 0.0, totalWidth, height)
    val path = UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = height / 2.0)

    // Тень
    /*
    // Рисовать тень внутри UIGraphics сложнее, проще добавить прозрачность или не делать вовсе для производительности.
    // Если очень нужно:
    CGContextSetShadowWithColor(context, CGSizeMake(0.0, 2.0), 4.0, UIColor.blackColor.colorWithAlphaComponent(0.2).CGColor)
    */

    bgColor.setFill()
    path.fill()

    // 2. Обводка (если не активен)
    if (!isActive) {
        strokeColor.setStroke()
        path.lineWidth = 1.5
        // Делаем inset, чтобы обводка не обрезалась
        val strokeRect = CGRectMake(0.75, 0.75, totalWidth - 1.5, height - 1.5)
        val strokePath = UIBezierPath.bezierPathWithRoundedRect(strokeRect, cornerRadius = (height - 1.5) / 2.0)
        strokePath.stroke()
    }

    // 3. Эмодзи
    val emojiAttributes = mapOf(
        NSFontAttributeName to emojiFont,
        // Для эмодзи цвет не важен, но для текста да
        // Если это не эмодзи шрифт, нужен цвет
    )
    val nsEmoji = NSString.create(string = emoji)
    val emojiSize = nsEmoji.sizeWithAttributes(emojiAttributes)

    val iconX = if (isActive) padding + (iconSize / 2.0) - (emojiSize.useContents { width } / 2.0)
    else (totalWidth / 2.0) - (emojiSize.useContents { width } / 2.0)

    // Центрирование по Y
    val iconY = (height / 2.0) - (emojiSize.useContents { height } / 2.0)

    nsEmoji.drawAtPoint(CGPointMake(iconX, iconY), withAttributes = emojiAttributes)

    // 4. Текст (Label)
    if (isActive) {
        val textX = padding + iconSize + 8.0
        val textY = (height / 2.0) - (labelSize.useContents { height } / 2.0)

        nsLabel.drawAtPoint(CGPointMake(textX, textY), withAttributes = labelAttributes as Map<Any?, *>)
    }

    val finalImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return finalImage ?: UIImage()
}