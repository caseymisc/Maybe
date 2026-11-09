package com.caseymisc.fitdash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caseymisc.fitdash.data.health.HealthConnectAvailability
import com.caseymisc.fitdash.data.health.HealthSnapshot
import com.caseymisc.fitdash.data.hevy.HevyWorkout
import com.caseymisc.fitdash.data.hevy.WeekBucket
import com.caseymisc.fitdash.data.hevy.startOfWeek
import com.caseymisc.fitdash.data.hevy.weeklyBuckets
import com.caseymisc.fitdash.data.settings.Settings
import com.caseymisc.fitdash.data.settings.WeightUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

sealed interface HealthState {
    data object Loading : HealthState
    data class Unavailable(val availability: HealthConnectAvailability) : HealthState
    data object NeedsPermissions : HealthState
    data class Ready(val snapshot: HealthSnapshot) : HealthState
    data class Error(val message: String) : HealthState
}

data class HevyDashboard(
    val workouts: List<HevyWorkout>,
    val weeks: List<WeekBucket>,
    val totalWorkouts: Int?,
)

sealed interface HevyState {
    data object Loading : HevyState
    data object NotConfigured : HevyState
    data class Ready(val data: HevyDashboard) : HevyState
    data class Error(val message: String) : HevyState
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as FitDashApp).container

    val settings: StateFlow<Settings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _health = MutableStateFlow<HealthState>(HealthState.Loading)
    val health: StateFlow<HealthState> = _health.asStateFlow()

    private val _hevy = MutableStateFlow<HevyState>(HevyState.Loading)
    val hevy: StateFlow<HevyState> = _hevy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val healthPermissions: Set<String> get() = container.health.requiredPermissions

    init {
        // Reload Hevy whenever the API key changes (including the first read from disk).
        viewModelScope.launch {
            container.settings.settings
                .map { it.hevyApiKey }
                .distinctUntilChanged()
                .collect { loadHevy(it) }
        }
        viewModelScope.launch { loadHealth() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val health = async { loadHealth() }
            val hevy = async { loadHevy(settings.value.hevyApiKey) }
            health.await()
            hevy.await()
            _refreshing.value = false
        }
    }

    fun refreshHealth() {
        viewModelScope.launch { loadHealth() }
    }

    fun saveHevyApiKey(key: String) {
        viewModelScope.launch { container.settings.setHevyApiKey(key) }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { container.settings.setWeightUnit(unit) }
    }

    private suspend fun loadHealth() {
        val repo = container.health
        val availability = repo.availability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            _health.value = HealthState.Unavailable(availability)
            return
        }
        if (_health.value !is HealthState.Ready) _health.value = HealthState.Loading
        _health.value = try {
            val granted = repo.grantedPermissions()
            if (granted.intersect(repo.requiredPermissions).isEmpty()) {
                HealthState.NeedsPermissions
            } else {
                HealthState.Ready(repo.loadSnapshot(granted))
            }
        } catch (e: Exception) {
            HealthState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun loadHevy(apiKey: String) {
        if (apiKey.isBlank()) {
            _hevy.value = HevyState.NotConfigured
            return
        }
        if (_hevy.value !is HevyState.Ready) _hevy.value = HevyState.Loading
        _hevy.value = try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val since = today.startOfWeek().minusWeeks(WEEKS - 1L).atStartOfDay(zone).toInstant()
            val workouts = container.hevy.workoutsSince(apiKey, since)
            val total = runCatching { container.hevy.workoutCount(apiKey) }.getOrNull()
            HevyState.Ready(
                HevyDashboard(
                    workouts = workouts,
                    weeks = weeklyBuckets(workouts, WEEKS, today, zone),
                    totalWorkouts = total,
                )
            )
        } catch (e: Exception) {
            HevyState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val WEEKS = 8
    }
}
