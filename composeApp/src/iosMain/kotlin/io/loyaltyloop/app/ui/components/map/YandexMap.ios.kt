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
import io.loyaltyloop.shared.models.GeoLocation
import kotlinx.cinterop.BetaInteropApi
import kotlinx.datetime.Clock
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
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
    val mapView = remember { YMKMapView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) }
    val mapObjects = remember { mapView.mapWindow!!.map.mapObjects.addCollection() }

    val tapListener = remember { MapObjectTapListenerImpl(onMarkerClick) }
    val inputListener = remember { InputListenerImpl(onMapClick) }

    DisposableEffect(mapView) {
        mapView.mapWindow!!.map.addInputListenerWithInputListener(inputListener)
        onDispose {
            mapView.mapWindow!!.map.removeInputListenerWithInputListener(inputListener)
        }
    }

    LaunchedEffect(cameraPosition) {
        val targetPoint = YMKPoint.pointWithLatitude(cameraPosition.lat, longitude = cameraPosition.lon)
        val animation = YMKAnimation.animationWithType(YMKAnimationType.YMKAnimationTypeSmooth, duration = 0.5f)
        
        // Исправлено имя параметра: animation вместо animationType
        mapView.mapWindow!!.map.moveWithCameraPosition(
            cameraPosition = YMKCameraPosition.cameraPositionWithTarget(
                targetPoint,
                zoom = cameraPosition.zoom,
                azimuth = 0.0f,
                tilt = 0.0f
            ),
            animation = animation, 
            cameraCallback = null
        )
    }

    val searchCircle = remember { mutableStateOf<YMKCircleMapObject?>(null) }
    val strokeColor = remember { UIColor(red = 0.145, green = 0.388, blue = 0.922, alpha = 1.0) }
    val fillColor = remember { UIColor(red = 0.145, green = 0.388, blue = 0.922, alpha = 0.1) }

    LaunchedEffect(searchAreaCenter, searchRadius) {
        if (searchAreaCenter != null && searchRadius != null) {
            val center = YMKPoint.pointWithLatitude(searchAreaCenter.lat, longitude = searchAreaCenter.lon)
            val circleGeom = YMKCircle.circleWithCenter(center, radius = searchRadius.toFloat())

            if (searchCircle.value == null) {
                // Сначала создаем, потом настраиваем
                val c = mapObjects.addCircleWithCircle(circleGeom)
                c.strokeColor = strokeColor
                c.strokeWidth = 1.5f
                c.fillColor = fillColor
                searchCircle.value = c
            } else {
                searchCircle.value?.geometry = circleGeom
            }
        } else {
            searchCircle.value?.let { mapObjects.removeWithMapObject(it) }
            searchCircle.value = null
        }
    }

    val userLocationPlacemark = remember { mutableStateOf<YMKPlacemarkMapObject?>(null) }
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            val point = YMKPoint.pointWithLatitude(userLocation.lat, longitude = userLocation.lon)
            val image = generateUserLocationImage()
            if (userLocationPlacemark.value == null) {
                val p = mapObjects.addPlacemarkWithPoint(point)
                p.setIconWithImage(image)
                p.zIndex = 100.0f
                userLocationPlacemark.value = p
            } else {
                userLocationPlacemark.value?.geometry = point
                userLocationPlacemark.value?.setIconWithImage(image)
            }
        } else {
            userLocationPlacemark.value?.let { mapObjects.removeWithMapObject(it) }
            userLocationPlacemark.value = null
        }
    }

    LaunchedEffect(markers) {
        mapObjects.clear()
        markers.forEach { marker ->
            val image = generatePinImage(marker)
            val point = YMKPoint.pointWithLatitude(marker.lat, longitude = marker.lon)
            val placemark = mapObjects.addPlacemarkWithPoint(point)
            placemark.setIconWithImage(image)
            placemark.userData = marker.id
            placemark.addTapListenerWithTapListener(tapListener)
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier
    )
}

@OptIn(ExperimentalForeignApi::class)
private class MapObjectTapListenerImpl(val onMarkerClick: (String) -> Unit) : NSObject(), YMKMapObjectTapListenerProtocol {
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
private class InputListenerImpl(val onMapClick: () -> Unit) : NSObject(), YMKMapInputListenerProtocol {
    override fun onMapTapWithMap(map: YMKMap, point: YMKPoint) {
        onMapClick()
    }
    override fun onMapLongTapWithMap(map: YMKMap, point: YMKPoint) {}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun generatePinImage(marker: MapMarker): UIImage {
    val isActive = marker.isSelected
    val bgActive = UIColor(red = 0.067, green = 0.094, blue = 0.153, alpha = 1.0)
    val bgInactive = UIColor.whiteColor
    val textColor = if (isActive) UIColor.whiteColor else UIColor(red = 0.122, green = 0.161, blue = 0.216, alpha = 1.0)
    val strokeColor = if (isActive) bgActive else UIColor(red = 0.145, green = 0.388, blue = 0.922, alpha = 1.0)
    val bgColor = if (isActive) bgActive else bgInactive

    val emoji = getEmojiForType(marker.type)
    val label = if (isActive) getLabelResource(marker.type) else ""

    val height = if (isActive) 40.0 else 36.0
    val padding = 12.0
    val iconSize = 20.0
    val labelFont = UIFont.boldSystemFontOfSize(13.0)
    val emojiFont = UIFont.systemFontOfSize(if (isActive) 18.0 else 20.0)

    val labelAttributes = mapOf<Any?, Any?>(
        NSFontAttributeName to labelFont,
        NSForegroundColorAttributeName to textColor
    )
    val nsLabel = NSString.create(string = label.toString())
    val labelSize = nsLabel.sizeWithAttributes(labelAttributes)
    val textWidth = if (isActive) labelSize.useContents { width } else 0.0
    val totalWidth = if (isActive) (padding * 2) + iconSize + 8.0 + textWidth else height
    val size = CGSizeMake(totalWidth, height)

    UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
    val rect = CGRectMake(0.0, 0.0, totalWidth, height)
    val path = UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = height / 2.0)
    bgColor.setFill()
    path.fill()

    if (!isActive) {
        strokeColor.setStroke()
        path.lineWidth = 1.5
        val strokeRect = CGRectMake(0.75, 0.75, totalWidth - 1.5, height - 1.5)
        val strokePath = UIBezierPath.bezierPathWithRoundedRect(strokeRect, cornerRadius = (height - 1.5) / 2.0)
        strokePath.stroke()
    }

    val emojiAttributes = mapOf<Any?, Any?>(NSFontAttributeName to emojiFont)
    val nsEmoji = NSString.create(string = emoji)
    val emojiSize = nsEmoji.sizeWithAttributes(emojiAttributes)
    val iconX = if (isActive) padding + (iconSize / 2.0) - (emojiSize.useContents { width } / 2.0) else (totalWidth / 2.0) - (emojiSize.useContents { width } / 2.0)
    val iconY = (height / 2.0) - (emojiSize.useContents { height } / 2.0)
    nsEmoji.drawAtPoint(CGPointMake(iconX, iconY), withAttributes = emojiAttributes)

    if (isActive) {
        val textX = padding + iconSize + 8.0
        val textY = (height / 2.0) - (labelSize.useContents { height } / 2.0)
        nsLabel.drawAtPoint(CGPointMake(textX, textY), withAttributes = labelAttributes)
    }

    val finalImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return finalImage ?: UIImage()
}

@OptIn(ExperimentalForeignApi::class)
private fun generateUserLocationImage(): UIImage {
    val size = CGSizeMake(24.0, 24.0)
    UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
    val white = UIColor.whiteColor
    white.setFill()
    val rect = CGRectMake(0.0, 0.0, 24.0, 24.0)
    UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = 12.0).fill()
    val blue = UIColor(red = 0.145, green = 0.388, blue = 0.922, alpha = 1.0)
    blue.setFill()
    val dotRect = CGRectMake(4.0, 4.0, 16.0, 16.0)
    UIBezierPath.bezierPathWithRoundedRect(dotRect, cornerRadius = 8.0).fill()
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image ?: UIImage()
}
