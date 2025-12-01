package io.loyaltyloop.app.features.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.map.CameraPosition
import io.loyaltyloop.app.ui.components.map.MapMarker
import io.loyaltyloop.app.ui.components.map.getLabelResource
import io.loyaltyloop.app.utils.GeoLocation
import io.loyaltyloop.app.utils.LocationService
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


private val bishkekLocation = GeoLocation(42.8746, 74.5698)

class PointsMapScreenModel(
    private val repository: PartnerRepository,
    private val locationService: LocationService
) : ScreenModel {

    data class State(
        // Камера (меняется при зуме и кликах на пины)
        val cameraPosition: CameraPosition = CameraPosition(bishkekLocation.lat, bishkekLocation.lon, 14f),

        // Центр поиска (меняется ТОЛЬКО при поиске или геолокации)
        // Нужен, чтобы список не пересортировывался при просмотре карты
        val searchCenter: GeoLocation = bishkekLocation,

        // Данные
        val points: List<TradingPointDto> = emptyList(),
        val markers: List<MapMarker> = emptyList(),
        val visiblePoints: List<TradingPointDto> = emptyList(),

        // UI
        val selectedPointId: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null,

        // Фильтры
        val query: String = "",
        val typeFilter: TradingPointType? = null,
        val radiusMeters: Int = 3000,
        val openNow: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Стартовая загрузка
        loadPoints()
    }

    // --- Actions ---

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        debouncedLoad()
    }

    fun onTypeFilterChanged(type: TradingPointType?) {
        _state.update { it.copy(typeFilter = type) }
        loadPoints()
    }

    fun onOpenNowChanged(isOpen: Boolean) {
        _state.update { it.copy(openNow = isOpen) }
        loadPoints()
    }

    fun onMapClicked() {
        // Снимаем выделение, но зум не трогаем
        _state.update { it.copy(selectedPointId = null) }
        updateMarkersVisuals()
    }

    fun onMarkerClicked(pointId: String) {
        val point = _state.value.points.find { it.id == pointId } ?: return

        _state.update {
            it.copy(
                selectedPointId = pointId,
                // ПЛАВНЫЙ ЗУМ И ПЕРЕМЕЩЕНИЕ:
                // Меняем только камеру, но не searchCenter!
                cameraPosition = it.cameraPosition.copy(
                    lat = point.latitude ?: it.cameraPosition.lat,
                    lon = point.longitude ?: it.cameraPosition.lon,
                    // Если пользователь далеко - приближаем. Если уже близко - оставляем как есть.
                    zoom = if (it.cameraPosition.zoom < 15f) 16f else it.cameraPosition.zoom
                )
            )
        }
        // Обновляем только визуал маркеров (цвет), без перезагрузки данных
        updateMarkersVisuals()
    }

    fun onLocateMe() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val location = locationService.getCurrentLocation()

            if (location != null) {
                _state.update {
                    it.copy(
                        searchCenter = location, // Обновляем центр поиска
                        cameraPosition = CameraPosition(location.lat, location.lon, 15f), // И камеру
                        selectedPointId = null,
                        isLoading = false
                    )
                }
                loadPoints() // Грузим новые данные
            } else {
                _state.update { it.copy(isLoading = false) } // Можно добавить error = "GPS недоступен"
            }
        }
    }

    // --- Internal Logic ---

    private fun debouncedLoad() {
        searchJob?.cancel()
        searchJob = screenModelScope.launch {
            delay(500)
            loadPoints()
        }
    }

    private fun loadPoints() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val s = _state.value

            val result = repository.searchPublicPoints(
                lat = s.searchCenter.lat,
                lon = s.searchCenter.lon,
                radius = s.radiusMeters,
                query = s.query,
                type = s.typeFilter,
                openNow = s.openNow
            )

            result.onSuccess { response ->
                // АНТИ-МЕРЦАНИЕ:
                // Если пришел тот же список (по ID), не обновляем стейт точек, чтобы не триггерить рекомпозицию карты
                if (!arePointsEqual(_state.value.points, response.points)) {
                    _state.update {
                        it.copy(
                            points = response.points,
                            visiblePoints = response.points, // Тут можно доп. фильтровать если надо
                            isLoading = false
                        )
                    }
                    updateMarkersVisuals()
                } else {
                    // Данные те же, просто снимаем флаг загрузки
                    _state.update { it.copy(isLoading = false) }
                }
            }.onFailure {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить точки") }
            }
        }
    }

    // Сравнение списков, чтобы избежать лишних перерисовок
    private fun arePointsEqual(prev: List<TradingPointDto>, next: List<TradingPointDto>): Boolean {
        if (prev.size != next.size) return false
        // Сравниваем ID. Если порядок важен - можно так. Если нет - через toSet()
        return prev.map { it.id } == next.map { it.id }
    }

    // Обновление только внешнего вида маркеров (выбран/не выбран) без запроса к сети
    private fun updateMarkersVisuals() {
        val s = _state.value
        val newMarkers = s.points.mapNotNull { point ->
            if (point.latitude == null || point.longitude == null) return@mapNotNull null

            MapMarker(
                id = point.id,
                lat = point.latitude!!,
                lon = point.longitude!!,
                title = point.name,
                type =  point.type  ,
                isSelected = point.id == s.selectedPointId,
                // Если в DTO появится логотип, прокидываем сюда
                logoUrl = (point as? Any)?.let { null }
            )
        }
        _state.update { it.copy(markers = newMarkers) }
    }
}